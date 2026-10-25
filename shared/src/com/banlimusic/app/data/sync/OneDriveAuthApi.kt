package com.banlimusic.app.data.sync

import com.banlimusic.app.core.network.awaitResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.net.SocketTimeoutException

data class OneDriveAuthConfig(
    val clientId: String,
    /** Must exactly match the URI registered for the Android platform in Entra. */
    val redirectUri: String,
    val tenant: String = "consumers",
    val scopes: List<String> = DEFAULT_SCOPES,
    val baseUrl: String = "https://login.microsoftonline.com",
) {
    val isConfigured: Boolean get() = clientId.isNotBlank() && redirectUri.isNotBlank()

    companion object {
        val DEFAULT_SCOPES = listOf(
            "offline_access",
            "Files.ReadWrite.AppFolder",
            "User.Read",
        )
    }
}

@Serializable
data class TokenResponse(
    @SerialName("access_token") val accessToken: String,
    @SerialName("refresh_token") val refreshToken: String? = null,
    @SerialName("expires_in") val expiresIn: Long = 3600L,
    @SerialName("token_type") val tokenType: String = "Bearer",
    @SerialName("scope") val scope: String? = null,
)

/**
 * Minimal Microsoft identity platform client: authorization-code + PKCE and
 * refresh-token exchange. No client secret is used because this is a public
 * native client.
 */
class OneDriveAuthApi(
    private val client: OkHttpClient,
    private val json: Json,
    private val config: OneDriveAuthConfig,
) {

    fun authorizationUrl(state: String, codeChallenge: String): HttpUrl {
        val base = "${config.baseUrl.trimEnd('/')}/${config.tenant}/oauth2/v2.0/authorize".toHttpUrl()
        return base.newBuilder()
            .addQueryParameter("client_id", config.clientId)
            .addQueryParameter("response_type", "code")
            .addQueryParameter("redirect_uri", config.redirectUri)
            .addQueryParameter("response_mode", "query")
            .addQueryParameter("scope", config.scopes.joinToString(" "))
            .addQueryParameter("state", state)
            .addQueryParameter("code_challenge", codeChallenge)
            .addQueryParameter("code_challenge_method", "S256")
            .addQueryParameter("prompt", "select_account")
            .build()
    }

    suspend fun exchangeCode(code: String, codeVerifier: String): TokenResponse {
        val form = FormBody.Builder()
            .add("client_id", config.clientId)
            .add("grant_type", "authorization_code")
            .add("code", code)
            .add("redirect_uri", config.redirectUri)
            .add("code_verifier", codeVerifier)
            .add("scope", config.scopes.joinToString(" "))
            .build()
        return tokenRequest(form)
    }

    suspend fun refresh(refreshToken: String): TokenResponse {
        val form = FormBody.Builder()
            .add("client_id", config.clientId)
            .add("grant_type", "refresh_token")
            .add("refresh_token", refreshToken)
            .add("scope", config.scopes.joinToString(" "))
            .build()
        return tokenRequest(form)
    }

    private suspend fun tokenRequest(form: FormBody): TokenResponse = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("${config.baseUrl.trimEnd('/')}/${config.tenant}/oauth2/v2.0/token")
            .post(form)
            .header("Accept", "application/json")
            .build()
        val response = try {
            client.newCall(request).awaitResponse()
        } catch (e: SocketTimeoutException) {
            throw SyncException.Timeout(e)
        } catch (e: IOException) {
            throw SyncException.Network(e)
        }
        response.use {
            val body = it.body?.string().orEmpty()
            if (!it.isSuccessful) {
                val error = runCatching {
                    json.decodeFromString(TokenError.serializer(), body)
                }.getOrNull()
                if (it.code == 400 && error?.error == "invalid_grant") {
                    throw SyncException.AuthRequired()
                }
                if (it.code == 401 || it.code == 403) throw SyncException.AuthRequired()
                throw SyncException.Unavailable(
                    error?.errorDescription?.takeIf { text -> text.isNotBlank() }
                        ?: "登录失败（${it.code}）",
                )
            }
            runCatching { json.decodeFromString(TokenResponse.serializer(), body) }
                .getOrElse { throw SyncException.Parse(it) }
        }
    }

    @Serializable
    private data class TokenError(
        val error: String? = null,
        @SerialName("error_description") val errorDescription: String? = null,
    )
}
