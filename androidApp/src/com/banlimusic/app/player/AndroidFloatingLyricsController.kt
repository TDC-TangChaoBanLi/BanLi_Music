/**
 * 文件作用：在系统悬浮窗中显示 Android 桌面歌词，并跟随播放进度与设置更新。
 */
package com.banlimusic.app.player

import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.provider.Settings
import android.view.Gravity
import android.view.WindowManager
import com.banlimusic.app.AppContainer
import com.banlimusic.app.MusicApplication
import com.banlimusic.app.core.datastore.AppSettings
import com.banlimusic.app.core.common.LrcLine
import com.banlimusic.app.core.model.Track
import com.banlimusic.app.domain.playback.LrcParser
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Owns the Android overlay view. Lyrics are loaded once per track and the
 * played/upcoming lines are recomputed from the shared playback state ticker.
 */
class AndroidFloatingLyricsController(
    private val app: MusicApplication,
    private val container: AppContainer,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val windowManager = app.getSystemService(WindowManager::class.java)
    private val density = app.resources.displayMetrics.density

    private var view: FloatingLyricsView? = null
    private var layoutParams: WindowManager.LayoutParams? = null
    private var currentSettings = AppSettings()
    private var track: Track? = null
    private var lines: List<LrcLine> = emptyList()
    private var positionMs = 0L
    private var lyricsJob: Job? = null
    private var started = false
    private val baseFlags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN

    fun start() {
        if (started) return
        started = true
        scope.launch {
            container.settingsRepository.settings.collect { settings ->
                currentSettings = settings
                updateOverlay()
            }
        }
        scope.launch {
            container.playbackController.state.collect { state ->
                positionMs = state.positionMs
                val nextTrack = state.currentTrack
                if (nextTrack?.id != track?.id) {
                    track = nextTrack
                    loadLyrics(nextTrack)
                }
                updateOverlay()
            }
        }
        // Re-check permission and refresh the line even when playback state is
        // unchanged (for example after returning from the overlay permission page).
        scope.launch {
            while (true) {
                updateOverlay()
                delay(500L)
            }
        }
    }

    fun stop() {
        lyricsJob?.cancel()
        lyricsJob = null
        removeOverlay()
        scope.cancel()
    }

    private fun loadLyrics(nextTrack: Track?) {
        lyricsJob?.cancel()
        lines = emptyList()
        if (nextTrack == null) return
        lyricsJob = scope.launch {
            val loaded = try {
                container.musicRepository.lyrics(nextTrack)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Throwable) {
                null
            }
            if (track?.id == nextTrack.id) {
                lines = loaded?.raw?.let(LrcParser::parse).orEmpty()
                updateOverlay()
            }
        }
    }

    private fun updateOverlay() {
        val show = currentSettings.floatingLyrics &&
            track != null &&
            Settings.canDrawOverlays(app)
        if (!show) {
            removeOverlay()
            return
        }
        val overlay = ensureOverlay() ?: return
        val desiredWidth = (app.resources.displayMetrics.widthPixels -
            (24 * density).toInt()).coerceAtLeast((240 * density).toInt())
        val locked = currentSettings.floatingLyricsLocked
        overlay.setLocked(locked)
        layoutParams?.let { params ->
            var changed = false
            if (params.width != desiredWidth) {
                params.width = desiredWidth
                changed = true
            }
            val desiredFlags = if (locked) {
                baseFlags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
            } else {
                baseFlags
            }
            if (params.flags != desiredFlags) {
                params.flags = desiredFlags
                changed = true
            }
            if (changed) {
                runCatching { windowManager.updateViewLayout(overlay, params) }
            }
        }
        overlay.applyStyle(
            played = currentSettings.floatingLyricsPlayedColor ?: Color.WHITE,
            unplayed = currentSettings.floatingLyricsUnplayedColor
                ?: Color.argb(170, 255, 255, 255),
            sizeSp = currentSettings.floatingLyricsFontSize.toFloat(),
            lines = currentSettings.floatingLyricsLines,
        )
        val currentIndex = lines.indexOfLast { it.timeMs <= positionMs }
        val currentLine = if (currentIndex >= 0) {
            lines.getOrNull(currentIndex)?.text
        } else {
            lines.firstOrNull()?.text
        } ?: "暂无歌词"
        val nextLine = if (currentSettings.floatingLyricsLines >= 2) {
            val nextIndex = if (currentIndex >= 0) currentIndex + 1 else 1
            lines.getOrNull(nextIndex)?.text?.takeIf { it != currentLine }
        } else {
            null
        }
        overlay.setLyrics(currentLine, nextLine)
    }

    private fun ensureOverlay(): FloatingLyricsView? {
        view?.let { return it }
        val created = FloatingLyricsView(app).apply {
            onClose = {
                scope.launch {
                    container.settingsRepository.setFloatingLyrics(false)
                }
                removeOverlay()
            }
            onLockChanged = { locked ->
                scope.launch {
                    container.settingsRepository.setFloatingLyricsLocked(locked)
                }
            }
        }
        val metrics = app.resources.displayMetrics
        val layoutParams = WindowManager.LayoutParams(
            (metrics.widthPixels - (24 * density).toInt()).coerceAtLeast((240 * density).toInt()),
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            },
            baseFlags,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            y = (96 * density).toInt()
        }
        created.onDragStart = {
            val location = IntArray(2)
            created.getLocationOnScreen(location)
            layoutParams.gravity = Gravity.TOP or Gravity.START
            layoutParams.x = location[0]
            layoutParams.y = location[1]
            runCatching { windowManager.updateViewLayout(created, layoutParams) }
        }
        created.onDragBy = { dx, dy ->
            val screenWidth = app.resources.displayMetrics.widthPixels
            val screenHeight = app.resources.displayMetrics.heightPixels
            layoutParams.x = (layoutParams.x + dx.toInt()).coerceIn(
                -created.width / 2,
                screenWidth - created.width / 2,
            )
            layoutParams.y = (layoutParams.y + dy.toInt()).coerceIn(
                0,
                (screenHeight - created.height).coerceAtLeast(0),
            )
            runCatching { windowManager.updateViewLayout(created, layoutParams) }
        }
        val locked = currentSettings.floatingLyricsLocked
        created.setLocked(locked)
        if (locked) {
            layoutParams.flags = baseFlags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        }
        val added = runCatching { windowManager.addView(created, layoutParams) }.isSuccess
        if (!added) return null
        view = created
        this.layoutParams = layoutParams
        return created
    }

    private fun removeOverlay() {
        val current = view ?: return
        runCatching { windowManager.removeView(current) }
        view = null
        layoutParams = null
    }
}
