package com.banlimusic.app.core.network

import com.banlimusic.app.core.common.MusicException
import com.banlimusic.app.core.limiter.RequestScheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.net.SocketTimeoutException
import java.util.concurrent.TimeUnit

class JsonHttpClient(
    private val client: OkHttpClient,
    private val json: Json,
    private val scheduler: RequestScheduler,
    private val defaultRetryAfterMs: Long = TimeUnit.MINUTES.toMillis(5),
) {

    suspend fun getJson(url: HttpUrl): JsonElement = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/json")
            .header("User-Agent", USER_AGENT)
            .build()

        val body = scheduler.execute {
            try {
                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        response.body.string()
                    } else {
                        when (response.code) {
                            401, 403 -> throw MusicException.Unauthorized()
                            404, 410 -> throw MusicException.NotFound()
                            429 -> {
                                val retryAfterMs = parseRetryAfter(response.header("Retry-After"))
                                scheduler.penalize(retryAfterMs)
                                throw MusicException.RateLimited(retryAfterMs)
                            }

                            in 500..599 -> throw MusicException.Server(response.code)
                            else -> throw MusicException.Server(response.code)
                        }
                    }
                }
            } catch (timeout: SocketTimeoutException) {
                throw MusicException.Timeout(timeout)
            } catch (io: IOException) {
                throw MusicException.Network(io)
            }
        }

        try {
            json.parseToJsonElement(body)
        } catch (error: Exception) {
            throw MusicException.Parse(error)
        }
    }

    private fun parseRetryAfter(header: String?): Long {
        val seconds = header?.trim()?.toLongOrNull()
        return if (seconds != null && seconds > 0) {
            TimeUnit.SECONDS.toMillis(seconds)
        } else {
            defaultRetryAfterMs
        }
    }

    companion object {
        const val USER_AGENT = "BanLiMusic/0.1 (Android)"

        fun createClient(cacheDir: java.io.File? = null): OkHttpClient {
            val builder = OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(25, TimeUnit.SECONDS)
                .writeTimeout(25, TimeUnit.SECONDS)
                .callTimeout(35, TimeUnit.SECONDS)
                .retryOnConnectionFailure(true)
            if (cacheDir != null) {
                builder.cache(okhttp3.Cache(java.io.File(cacheDir, "http"), 16L * 1024 * 1024))
            }
            return builder.build()
        }
    }
}
