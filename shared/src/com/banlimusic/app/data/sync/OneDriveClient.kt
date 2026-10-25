package com.banlimusic.app.data.sync

import com.banlimusic.app.core.limiter.RequestScheduler
import com.banlimusic.app.core.limiter.SlidingWindowRateLimiter
import com.banlimusic.app.core.model.FavoriteSyncDocument
import com.banlimusic.app.core.model.SYNC_FILE_NAME
import com.banlimusic.app.core.network.awaitResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.net.SocketTimeoutException

fun interface AccessTokenProvider {
    suspend fun accessToken(forceRefresh: Boolean): String
}

data class RemoteDocument(
    val document: FavoriteSyncDocument?,
    val etag: String?,
)

data class OneDriveAccount(
    val displayName: String?,
    val accountName: String?,
)

/**
 * Microsoft Graph wrapper for the single sync file in the app folder
 * (`Apps/<application name>/library.json`).
 *
 * Requests use their own scheduler: the GD Music rate limit must never be
 * consumed by cloud sync traffic.
 */
class OneDriveClient(
    private val client: OkHttpClient,
    private val json: Json,
    private val tokenProvider: AccessTokenProvider,
    private val graphBaseUrl: String = "https://graph.microsoft.com/v1.0",
    private val scheduler: RequestScheduler = RequestScheduler(
        maxConcurrent = 2,
        limiter = SlidingWindowRateLimiter(
            limit = 120,
            windowMs = 60_000L,
            maxWaitMs = 10_000L,
        ),
    ),
) {

    suspend fun account(): OneDriveAccount = withContext(Dispatchers.IO) {
        val url = "${graphBaseUrl.trimEnd('/')}/me".toHttpUrl().newBuilder()
            .addQueryParameter("\$select", "displayName,userPrincipalName,mail")
            .build()
        val request = Request.Builder().url(url).get().build()
        val body = execute(request) { response ->
            response.body?.string().orEmpty()
        }
        val me = runCatching { json.decodeFromString(GraphUser.serializer(), body) }
            .getOrElse { throw SyncException.Parse(it) }
        OneDriveAccount(
            displayName = me.displayName?.takeIf { it.isNotBlank() },
            accountName = (me.mail ?: me.userPrincipalName)?.takeIf { it.isNotBlank() },
        )
    }

    suspend fun downloadLibrary(): RemoteDocument = withContext(Dispatchers.IO) {
        val metaUrl = "${graphBaseUrl.trimEnd('/')}/me/drive/special/approot:/$SYNC_FILE_NAME"
            .toHttpUrl().newBuilder()
            .addQueryParameter("\$select", "id,eTag,size")
            .build()
        val metaResponse = executeRaw(Request.Builder().url(metaUrl).get().build())
        val etag = metaResponse.use { response ->
            when {
                response.code == 404 ->
                    return@withContext RemoteDocument(document = null, etag = null)
                !response.isSuccessful -> throw errorFor(response)
                else -> {
                    val body = response.body?.string().orEmpty()
                    runCatching { json.decodeFromString(DriveItemMeta.serializer(), body) }
                        .getOrNull()?.eTag
                }
            }
        }

        val contentUrl =
            "${graphBaseUrl.trimEnd('/')}/me/drive/special/approot:/$SYNC_FILE_NAME:/content"
                .toHttpUrl()
        val contentResponse = executeRaw(Request.Builder().url(contentUrl).get().build())
        contentResponse.use { response ->
            when {
                response.code == 404 -> RemoteDocument(document = null, etag = etag)
                !response.isSuccessful -> throw errorFor(response)
                else -> {
                    val body = response.body?.string().orEmpty()
                    if (body.isBlank()) {
                        RemoteDocument(document = null, etag = etag)
                    } else {
                        val document = runCatching {
                            json.decodeFromString(FavoriteSyncDocument.serializer(), body)
                        }.getOrElse { throw SyncException.Parse(it) }
                        RemoteDocument(document = document, etag = etag)
                    }
                }
            }
        }
    }

    /** @return the new eTag when Graph reports one. */
    suspend fun uploadLibrary(
        document: FavoriteSyncDocument,
        etag: String?,
    ): String? = withContext(Dispatchers.IO) {
        val body = json.encodeToString(FavoriteSyncDocument.serializer(), document)
        val url = "${graphBaseUrl.trimEnd('/')}/me/drive/special/approot:/$SYNC_FILE_NAME:/content"
            .toHttpUrl()
        val request = Request.Builder()
            .url(url)
            .put(body.toRequestBody(JSON_MEDIA_TYPE))
            .apply {
                if (etag != null) {
                    header("If-Match", etag)
                } else {
                    header("If-None-Match", "*")
                }
            }
            .build()
        val response = executeRaw(request)
        response.use {
            if (it.code == 412) throw SyncException.Conflict()
            if (!it.isSuccessful) throw errorFor(it)
            val responseBody = it.body?.string().orEmpty()
            runCatching { json.decodeFromString(DriveItemMeta.serializer(), responseBody) }
                .getOrNull()?.eTag
        }
    }

    suspend fun deleteLibrary() = withContext(Dispatchers.IO) {
        val url = "${graphBaseUrl.trimEnd('/')}/me/drive/special/approot:/$SYNC_FILE_NAME"
            .toHttpUrl()
        val response = executeRaw(Request.Builder().url(url).delete().build())
        response.use {
            if (it.code == 404) return@withContext
            if (!it.isSuccessful) throw errorFor(it)
        }
    }

    private suspend fun <T> execute(request: Request, read: (okhttp3.Response) -> T): T {
        val response = executeRaw(request)
        return response.use {
            if (!it.isSuccessful) throw errorFor(it)
            read(it)
        }
    }

    private suspend fun executeRaw(request: Request): okhttp3.Response = withContext(Dispatchers.IO) {
        var token = tokenProvider.accessToken(forceRefresh = false)
        var response = send(request, token)
        if (response.code == 401) {
            response.close()
            token = tokenProvider.accessToken(forceRefresh = true)
            response = send(request, token)
        }
        if (response.code == 429) {
            val retryAfterMs = response.header("Retry-After")
                ?.toLongOrNull()
                ?.times(1000L)
                ?: 30_000L
            response.close()
            scheduler.penalize(retryAfterMs)
            throw SyncException.RateLimited(retryAfterMs)
        }
        response
    }

    private suspend fun send(request: Request, token: String): okhttp3.Response =
        try {
            scheduler.execute {
                client.newCall(
                    request.newBuilder()
                        .header("Authorization", "Bearer $token")
                        .header("Accept", "application/json")
                        .build(),
                ).awaitResponse()
            }
        } catch (e: SocketTimeoutException) {
            throw SyncException.Timeout(e)
        } catch (e: IOException) {
            throw SyncException.Network(e)
        }

    private fun errorFor(response: okhttp3.Response): SyncException {
        val message = runCatching {
            val raw = response.body?.string().orEmpty()
            json.decodeFromString(GraphErrorEnvelope.serializer(), raw)
                .error?.message
        }.getOrNull()
        return when (response.code) {
            401, 403 -> SyncException.AuthRequired()
            404 -> SyncException.NotFound()
            409, 412 -> SyncException.Conflict()
            in 500..599 -> SyncException.Server(response.code)
            else -> SyncException.Unavailable(
                message?.takeIf { it.isNotBlank() } ?: "OneDrive 请求失败（${response.code}）",
            )
        }
    }

    @Serializable
    private data class DriveItemMeta(
        val id: String? = null,
        val name: String? = null,
        @SerialName("eTag") val eTag: String? = null,
    )

    @Serializable
    private data class GraphUser(
        val displayName: String? = null,
        val userPrincipalName: String? = null,
        val mail: String? = null,
    )

    @Serializable
    private data class GraphErrorEnvelope(
        val error: GraphErrorDetail? = null,
    )

    @Serializable
    private data class GraphErrorDetail(
        val code: String? = null,
        val message: String? = null,
    )

    private companion object {
        val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}
