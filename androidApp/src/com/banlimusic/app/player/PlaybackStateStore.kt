/**
 * 文件作用：持久化播放队列、当前索引与进度，供播放服务恢复。
 */
package com.banlimusic.app.player

import android.content.Context
import androidx.core.content.edit
import com.banlimusic.app.core.model.Track
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

data class RestoredPlayback(
    val tracks: List<Track>,
    val index: Int,
    val positionMs: Long,
)

/**
 * Small persisted snapshot of the playback queue. It lets the widget's play
 * button restore the last queue after Android has stopped the media service.
 * Temporary stream URLs are never stored.
 */
object PlaybackStateStore {
    private const val PREFS = "playback_state"
    private const val KEY_QUEUE = "queue_json"
    private const val KEY_INDEX = "index"
    private const val KEY_POSITION = "position_ms"

    fun save(context: Context, json: Json, tracks: List<Track>, index: Int, positionMs: Long) {
        if (tracks.isEmpty()) return
        val raw = runCatching {
            json.encodeToString(ListSerializer(Track.serializer()), tracks)
        }.getOrNull() ?: return
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit {
            putString(KEY_QUEUE, raw)
            putInt(KEY_INDEX, index.coerceIn(tracks.indices))
            putLong(KEY_POSITION, positionMs.coerceAtLeast(0L))
        }
    }

    fun load(context: Context, json: Json): RestoredPlayback? {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val raw = prefs.getString(KEY_QUEUE, null) ?: return null
        val tracks = runCatching {
            json.decodeFromString(ListSerializer(Track.serializer()), raw)
        }.getOrNull()?.takeIf { it.isNotEmpty() } ?: return null
        return RestoredPlayback(
            tracks = tracks,
            index = prefs.getInt(KEY_INDEX, 0).coerceIn(tracks.indices),
            positionMs = prefs.getLong(KEY_POSITION, 0L).coerceAtLeast(0L),
        )
    }

    fun clear(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit { clear() }
    }
}
