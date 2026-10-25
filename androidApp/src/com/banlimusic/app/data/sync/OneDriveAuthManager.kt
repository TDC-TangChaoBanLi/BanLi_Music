/**
 * 文件作用：完成 OneDrive 授权码登录、静默续期与退出登录。
 */
package com.banlimusic.app.data.sync

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.banlimusic.app.core.security.AndroidTokenStore
import com.banlimusic.app.core.security.StoredAuth
import com.banlimusic.app.core.sync.OneDriveConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import java.util.concurrent.atomic.AtomicBoolean

enum class AuthState { NOT_CONFIGURED, SIGNED_OUT, SIGNED_IN }

/**
 * Hand-rolled public-client OAuth. MSAL is intentionally not used: the project
 * avoids large dependencies and only needs auth-code + PKCE + refresh.
 */
class OneDriveAuthManager(
    private val context: Context,
    json: Json,
    httpClient: OkHttpClient,
    private val tokenStore: AndroidTokenStore,
    private val config: OneDriveAuthConfig,
) : AccessTokenProvider {

    private val api = OneDriveAuthApi(httpClient, json, config)
    private val refreshMutex = Mutex()
    private val callbackHandled = AtomicBoolean(false)

    private val _state = MutableStateFlow(initialState())
    val state: StateFlow<AuthState> = _state.asStateFlow()

    private fun initialState(): AuthState = when {
        !config.isConfigured -> AuthState.NOT_CONFIGURED
        tokenStore.load()?.hasSession == true -> AuthState.SIGNED_IN
        else -> AuthState.SIGNED_OUT
    }

    val isConfigured: Boolean get() = config.isConfigured

    fun isSignedIn(): Boolean = _state.value == AuthState.SIGNED_IN

    fun redirectUri(): String = config.redirectUri

    /** Opens the system browser; the result comes back through MainActivity. */
    fun signIn(): Result<Unit> {
        if (!config.isConfigured) return Result.failure(SyncException.NotConfigured())
        val existing = tokenStore.load()
        val verifier = Pkce.codeVerifier()
        val state = Pkce.state()
        val saved = runCatching {
            tokenStore.save(
                (existing ?: StoredAuth()).copy(
                    pendingState = state,
                    pendingVerifier = verifier,
                    pendingCreatedAt = System.currentTimeMillis(),
                ),
            )
        }
        if (saved.isFailure) {
            return Result.failure(
                SyncException.Unavailable("无法安全保存登录状态，请重试"),
            )
        }
        val url = api.authorizationUrl(state, Pkce.codeChallenge(verifier))
        return try {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse(url.toString()))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
            callbackHandled.set(false)
            Result.success(Unit)
        } catch (e: ActivityNotFoundException) {
            restoreSession(existing)
            Result.failure(
                SyncException.Unavailable("没有可用的浏览器，无法完成 OneDrive 登录"),
            )
        }
    }

    suspend fun completeSignIn(uri: Uri): Result<Unit> {
        if (!config.isConfigured) return Result.failure(SyncException.NotConfigured())
        if (!isRedirect(uri)) return Result.failure(SyncException.AuthRequired())
        if (!callbackHandled.compareAndSet(false, true)) {
            return Result.failure(SyncException.Unavailable("登录回调已处理"))
        }
        val error = uri.getQueryParameter("error")
        if (!error.isNullOrBlank()) {
            restoreSession(tokenStore.load())
            callbackHandled.set(false)
            val description = uri.getQueryParameter("error_description")
            return Result.failure(
                SyncException.Unavailable(
                    description?.takeIf { it.isNotBlank() } ?: "登录已取消或未获得授权",
                ),
            )
        }
        val code = uri.getQueryParameter("code")
        val returnedState = uri.getQueryParameter("state")
        if (code.isNullOrBlank() || returnedState.isNullOrBlank()) {
            callbackHandled.set(false)
            return Result.failure(SyncException.AuthRequired())
        }
        val stored = tokenStore.load()
        val verifier = stored?.pendingVerifier
        val expectedState = stored?.pendingState
        if (verifier.isNullOrBlank() || expectedState.isNullOrBlank() || expectedState != returnedState) {
            callbackHandled.set(false)
            return Result.failure(SyncException.AuthRequired())
        }
        if (System.currentTimeMillis() - stored.pendingCreatedAt > PENDING_TTL_MS) {
            restoreSession(stored)
            callbackHandled.set(false)
            return Result.failure(SyncException.AuthRequired())
        }
        return runCatching {
            val token = api.exchangeCode(code, verifier)
            saveToken(token, fallbackRefresh = null)
            _state.value = AuthState.SIGNED_IN
            Unit
        }.onFailure { throwable ->
            callbackHandled.set(false)
            if (throwable is SyncException.AuthRequired) {
                restoreSession(stored)
            }
        }
    }

    override suspend fun accessToken(forceRefresh: Boolean): String = refreshMutex.withLock {
        if (!config.isConfigured) throw SyncException.NotConfigured()
        val stored = tokenStore.load()
            ?: throw SyncException.NotSignedIn()
        if (!stored.hasSession) {
            _state.value = AuthState.SIGNED_OUT
            throw SyncException.NotSignedIn()
        }
        val now = System.currentTimeMillis()
        val cachedToken = stored.accessToken
        if (!forceRefresh && !cachedToken.isNullOrBlank() && stored.expiresAt - EXPIRY_MARGIN_MS > now) {
            return cachedToken
        }
        val refreshToken = stored.refreshToken?.takeIf { it.isNotBlank() }
            ?: throw SyncException.AuthRequired()
        val token = try {
            api.refresh(refreshToken)
        } catch (e: SyncException.AuthRequired) {
            tokenStore.clear()
            _state.value = AuthState.SIGNED_OUT
            throw e
        }
        saveToken(token, fallbackRefresh = refreshToken)
        _state.value = AuthState.SIGNED_IN
        token.accessToken
    }

    fun signOut() {
        tokenStore.clear()
        callbackHandled.set(false)
        _state.value = if (config.isConfigured) AuthState.SIGNED_OUT else AuthState.NOT_CONFIGURED
    }

    private fun saveToken(token: TokenResponse, fallbackRefresh: String?) {
        val refresh = token.refreshToken?.takeIf { it.isNotBlank() } ?: fallbackRefresh
        tokenStore.save(
            StoredAuth(
                refreshToken = refresh,
                accessToken = token.accessToken,
                expiresAt = System.currentTimeMillis() +
                    token.expiresIn.coerceAtLeast(60L) * 1_000L,
                scope = token.scope,
            ),
        )
    }

    /** Drops a pending OAuth session without touching an existing signed-in session. */
    private fun restoreSession(existing: StoredAuth?) {
        if (existing?.hasSession == true) {
            tokenStore.save(
                existing.copy(
                    pendingState = null,
                    pendingVerifier = null,
                    pendingCreatedAt = 0L,
                ),
            )
        } else {
            tokenStore.clear()
        }
        _state.value = when {
            !config.isConfigured -> AuthState.NOT_CONFIGURED
            existing?.hasSession == true -> AuthState.SIGNED_IN
            else -> AuthState.SIGNED_OUT
        }
    }

    private fun isRedirect(uri: Uri): Boolean =
        uri.scheme == OneDriveConfig.REDIRECT_SCHEME &&
            uri.host == OneDriveConfig.REDIRECT_HOST

    private companion object {
        const val PENDING_TTL_MS = 10L * 60L * 1_000L
        const val EXPIRY_MARGIN_MS = 60L * 1_000L
    }
}
