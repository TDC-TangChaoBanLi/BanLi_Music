package com.banlimusic.app.data.source.gd

import com.banlimusic.app.core.common.MusicException
import com.banlimusic.app.core.limiter.RateLimitStatus
import com.banlimusic.app.core.limiter.RequestScheduler
import com.banlimusic.app.core.network.JsonHttpClient
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl

class GdApiClient(
    private val http: JsonHttpClient,
    private val scheduler: RequestScheduler,
    private val baseUrl: HttpUrl = "https://music-api.gdstudio.xyz/api.php".toHttpUrl(),
) {

    suspend fun search(source: String, query: String, page: Int, pageSize: Int): JsonElement =
        http.getJson(
            apiUrl(
                "types" to "search",
                "source" to source,
                "name" to query,
                "count" to pageSize.toString(),
                "pages" to page.toString(),
            ),
        )

    suspend fun streamUrl(source: String, trackId: String, qualityKbps: Int): JsonElement =
        http.getJson(
            apiUrl(
                "types" to "url",
                "source" to source,
                "id" to trackId,
                "br" to qualityKbps.toString(),
            ),
        )

    suspend fun artworkUrl(source: String, picId: String, size: Int = 500): JsonElement =
        http.getJson(
            apiUrl(
                "types" to "pic",
                "source" to source,
                "id" to picId,
                "size" to size.toString(),
            ),
        )

    suspend fun lyrics(source: String, lyricId: String): JsonElement =
        http.getJson(
            apiUrl(
                "types" to "lyric",
                "source" to source,
                "id" to lyricId,
            ),
        )

    suspend fun rateLimitStatus(): RateLimitStatus = scheduler.status()

    private fun apiUrl(vararg params: Pair<String, String>): HttpUrl {
        val builder = baseUrl.newBuilder()
        params.forEach { (name, value) -> builder.addQueryParameter(name, value) }
        return builder.build()
    }
}

fun JsonElement?.stringOrNull(): String? {
    val primitive = this as? JsonPrimitive ?: return null
    if (!primitive.isString && primitive.content == "null") return null
    return primitive.content.takeIf { it.isNotBlank() && it != "null" }
}

fun JsonElement?.longOrNull(): Long? = stringOrNull()?.toLongOrNull()

fun JsonElement?.intOrNull(): Int? = (this as? JsonPrimitive)?.intOrNull

fun JsonElement?.stringList(): List<String> = when (this) {
    is JsonArray -> mapNotNull { it.stringOrNull() }
    is JsonPrimitive -> stringOrNull()?.let { listOf(it) }.orEmpty()
    else -> emptyList()
}

/**
 * The public API normally returns an array, but error objects and wrapped
 * payloads are handled too so a single bad response does not crash search.
 */
fun JsonElement.searchItems(): List<JsonObject> {
    val array = when (this) {
        is JsonArray -> this
        is JsonObject -> {
            this["error"].stringOrNull()?.let { throw MusicException.Unavailable(it) }
            val candidate = this["data"] ?: this["result"] ?: this["songs"]
            when (candidate) {
                is JsonArray -> candidate
                is JsonObject -> (candidate["songs"] as? JsonArray)
                    ?: (candidate["data"] as? JsonArray)
                    ?: JsonArray(emptyList())
                else -> JsonArray(emptyList())
            }
        }

        else -> throw MusicException.Parse()
    }
    return array.mapNotNull { it as? JsonObject }
}

fun JsonObject.errorMessage(): String? =
    this["error"].stringOrNull() ?: this["msg"].stringOrNull() ?: this["message"].stringOrNull()
