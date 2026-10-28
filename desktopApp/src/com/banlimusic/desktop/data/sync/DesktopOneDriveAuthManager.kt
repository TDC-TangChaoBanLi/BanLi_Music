package com.banlimusic.desktop.data.sync

import com.banlimusic.app.data.sync.AccessTokenProvider
import com.banlimusic.app.data.sync.OneDriveAuthApi
import com.banlimusic.app.data.sync.OneDriveAuthConfig
import com.banlimusic.app.data.sync.Pkce
import com.banlimusic.app.data.sync.SyncException
import com.banlimusic.app.data.sync.TokenResponse
import com.banlimusic.app.data.sync.syncUserMessage
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import java.awt.Desktop
import java.net.BindException
import java.net.InetSocketAddress
import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.concurrent.atomic.AtomicBoolean

enum class DesktopAuthState { NOT_CONFIGURED, SIGNED_OUT, SIGNED_IN }

/**
 * OAuth for the desktop client. A short-lived loopback HTTP server receives the
 * authorization code, so no custom URI scheme or Android-specific callback is
 * required. The redirect URI must be registered in Entra.
 */
class DesktopOneDriveAuthManager(
    private val scope: CoroutineScope,
    private val json: Json,
    private val httpClient: OkHttpClient,
    private val tokenStore: DesktopTokenStore,
    private val config: OneDriveAuthConfig,
) : AccessTokenProvider {

    private val api = OneDriveAuthApi(httpClient, json, config)
    private val refreshMutex = Mutex()
    private val signingIn = AtomicBoolean(false)

    private val initial = when {
        !config.isConfigured -> DesktopAuthState.NOT_CONFIGURED
        tokenStore.load()?.hasSession == true -> DesktopAuthState.SIGNED_IN
        else -> DesktopAuthState.SIGNED_OUT
    }
    private val _state = kotlinx.coroutines.flow.MutableStateFlow(initial)
    val state: kotlinx.coroutines.flow.StateFlow<DesktopAuthState> = _state

    val isConfigured: Boolean get() = config.isConfigured
    fun isSignedIn(): Boolean = _state.value == DesktopAuthState.SIGNED_IN
    fun redirectUri(): String = config.redirectUri

    fun signIn(onResult: (Result<Unit>) -> Unit) {
        if (!config.isConfigured) {
            onResult(Result.failure(SyncException.NotConfigured()))
            return
        }
        if (!signingIn.compareAndSet(false, true)) {
            onResult(Result.failure(SyncException.Unavailable("登录正在进行中")))
            return
        }
        scope.launch {
            var server: HttpServer? = null
            try {
                val verifier = Pkce.codeVerifier()
                val expectedState = Pkce.state()
                val callback = CompletableDeferred<Map<String, String>>()
                server = HttpServer.create(InetSocketAddress(0), 0)
                val callbackUri = "http://localhost:${server.address.port}$CALLBACK_PATH"
                val signInApi = OneDriveAuthApi(
                    httpClient,
                    json,
                    config.copy(redirectUri = callbackUri),
                )
                server.createContext(CALLBACK_PATH) { exchange ->
                    val parameters = parseQuery(exchange.requestURI.rawQuery)
                    val html = if (parameters["error"] != null) {
                        "登录未完成，请返回 BanLiMusic 查看原因。"
                    } else {
                        "登录完成，可以关闭此页面并返回 BanLiMusic。"
                    }
                    callback.complete(parameters)
                    val bytes = html.toByteArray(StandardCharsets.UTF_8)
                    exchange.responseHeaders.add("Content-Type", "text/html; charset=utf-8")
                    exchange.sendResponseHeaders(200, bytes.size.toLong())
                    exchange.responseBody.use { it.write(bytes) }
                }
                server.start()
                val url = signInApi.authorizationUrl(expectedState, Pkce.codeChallenge(verifier))
                openBrowser(url.toString())
                val parameters = withTimeout(CALLBACK_TIMEOUT_MS) { callback.await() }
                parameters["error"]?.let { error ->
                    val description = parameters["error_description"]
                    throw SyncException.Unavailable(
                        description?.takeIf { it.isNotBlank() } ?: "登录已取消或未获得授权（$error）",
                    )
                }
                if (parameters["state"] != expectedState) {
                    throw SyncException.AuthRequired()
                }
                val code = parameters["code"]?.takeIf { it.isNotBlank() }
                    ?: throw SyncException.AuthRequired()
                val token = signInApi.exchangeCode(code, verifier)
                saveToken(token, fallbackRefresh = null)
                _state.value = DesktopAuthState.SIGNED_IN
                onResult(Result.success(Unit))
            } catch (bind: BindException) {
                onResult(
                    Result.failure(
                        SyncException.Unavailable(
                            "无法启动本地登录回调服务，请检查防火墙或端口权限",
                        ),
                    ),
                )
            } catch (timeout: kotlinx.coroutines.TimeoutCancellationException) {
                onResult(Result.failure(SyncException.Timeout(timeout)))
            } catch (error: Throwable) {
                onResult(Result.failure(error))
            } finally {
                runCatching { server?.stop(0) }
                signingIn.set(false)
            }
        }
    }

    override suspend fun accessToken(forceRefresh: Boolean): String = refreshMutex.withLock {
        if (!config.isConfigured) throw SyncException.NotConfigured()
        val stored = tokenStore.load() ?: throw SyncException.NotSignedIn()
        if (!stored.hasSession) {
            _state.value = DesktopAuthState.SIGNED_OUT
            throw SyncException.NotSignedIn()
        }
        val now = System.currentTimeMillis()
        val cached = stored.accessToken
        if (!forceRefresh && !cached.isNullOrBlank() && stored.expiresAt - EXPIRY_MARGIN_MS > now) {
            return cached
        }
        val refresh = stored.refreshToken?.takeIf { it.isNotBlank() }
            ?: throw SyncException.AuthRequired()
        val token = try {
            api.refresh(refresh)
        } catch (error: SyncException.AuthRequired) {
            tokenStore.clear()
            _state.value = DesktopAuthState.SIGNED_OUT
            throw error
        }
        saveToken(token, fallbackRefresh = refresh)
        _state.value = DesktopAuthState.SIGNED_IN
        token.accessToken
    }

    fun signOut() {
        tokenStore.clear()
        _state.value = if (config.isConfigured) {
            DesktopAuthState.SIGNED_OUT
        } else {
            DesktopAuthState.NOT_CONFIGURED
        }
    }

    fun userMessage(error: Throwable): String = error.syncUserMessage()

    private fun saveToken(token: TokenResponse, fallbackRefresh: String?) {
        val refresh = token.refreshToken?.takeIf { it.isNotBlank() } ?: fallbackRefresh
        tokenStore.save(
            DesktopStoredAuth(
                refreshToken = refresh,
                accessToken = token.accessToken,
                expiresAt = System.currentTimeMillis() +
                    token.expiresIn.coerceAtLeast(60L) * 1_000L,
                scope = token.scope,
            ),
        )
    }

    private suspend fun openBrowser(url: String) = withContext(Dispatchers.IO) {
        if (!Desktop.isDesktopSupported() ||
            !Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)
        ) {
            throw SyncException.Unavailable("当前系统无法打开浏览器，请手动访问：$url")
        }
        runCatching { Desktop.getDesktop().browse(URI(url)) }
            .getOrElse { throw SyncException.Unavailable("无法打开浏览器，请检查默认浏览器设置") }
    }

    private fun parseQuery(raw: String?): Map<String, String> =
        raw.orEmpty()
            .split('&')
            .mapNotNull { part ->
                if (part.isBlank()) return@mapNotNull null
                val index = part.indexOf('=')
                if (index < 0) return@mapNotNull null
                val key = URLDecoder.decode(part.substring(0, index), StandardCharsets.UTF_8)
                val value = URLDecoder.decode(part.substring(index + 1), StandardCharsets.UTF_8)
                key to value
            }
            .toMap()

    private companion object {
        const val CALLBACK_PATH = "/callback"
        const val CALLBACK_TIMEOUT_MS = 5L * 60L * 1_000L
        const val EXPIRY_MARGIN_MS = 60L * 1_000L
    }
}
