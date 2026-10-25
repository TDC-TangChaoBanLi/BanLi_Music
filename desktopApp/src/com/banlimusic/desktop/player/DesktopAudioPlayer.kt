package com.banlimusic.desktop.player

import com.banlimusic.app.core.common.MusicException
import com.banlimusic.app.core.common.userMessage
import com.banlimusic.app.core.model.Track
import com.banlimusic.app.core.network.JsonHttpClient
import com.banlimusic.desktop.data.DesktopMusicRepository
import javazoom.jl.decoder.Bitstream
import javazoom.jl.decoder.Decoder
import javazoom.jl.decoder.SampleBuffer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.SourceDataLine

enum class DesktopPlayMode(val label: String) {
    SEQUENTIAL("顺序播放"),
    REPEAT_ALL("列表循环"),
    REPEAT_ONE("单曲循环"),
    SHUFFLE("随机播放"),
}

data class DesktopPlaybackState(
    val track: Track? = null,
    val queue: List<Track> = emptyList(),
    val index: Int = -1,
    val loading: Boolean = false,
    val playing: Boolean = false,
    val paused: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val error: String? = null,
    val playMode: DesktopPlayMode = DesktopPlayMode.SEQUENTIAL,
) {
    val hasQueue: Boolean get() = queue.isNotEmpty()
    val progress: Float
        get() = if (durationMs <= 0L) 0f
        else (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
}

/**
 * Deliberately small desktop player. JLayer supplies MP3 decoding and Java
 * Sound renders the PCM stream, which gives us pause/resume without pulling in
 * a large media framework. Lossless FLAC is left to a future backend.
 */
class DesktopAudioPlayer(
    private val scope: CoroutineScope,
    private val repository: DesktopMusicRepository,
    private val httpClient: OkHttpClient,
) {
    private val _state = MutableStateFlow(DesktopPlaybackState())
    val state: StateFlow<DesktopPlaybackState> = _state.asStateFlow()

    private var job: Job? = null
    private var generation = 0L
    @Volatile private var stopped = true
    @Volatile private var paused = false
    @Volatile private var playMode = DesktopPlayMode.SEQUENTIAL
    @Volatile private var input: InputStream? = null
    @Volatile private var line: SourceDataLine? = null

    fun play(track: Track, queue: List<Track> = listOf(track)) {
        val effectiveQueue = queue.ifEmpty { listOf(track) }
        val index = effectiveQueue.indexOfFirst { it.id == track.id }
            .takeIf { it >= 0 }
            ?: 0
        start(effectiveQueue, index)
    }

    fun playLocal(track: Track, file: File) {
        if (!file.isFile) return
        start(queue = listOf(track), index = 0, localFile = file)
    }

    fun togglePause() {
        val current = _state.value
        if (current.track == null || current.loading) return
        if (current.error != null) {
            start(current.queue.ifEmpty { listOf(current.track) }, current.index)
            return
        }
        if (!current.playing && !current.paused) {
            start(current.queue.ifEmpty { listOf(current.track) }, current.index)
            return
        }
        paused = !paused
        _state.update {
            it.copy(
                playing = !paused,
                paused = paused,
                error = null,
            )
        }
    }

    fun stop() {
        stopCurrent()
        _state.update {
            it.copy(
                loading = false,
                playing = false,
                paused = false,
                positionMs = 0L,
                error = null,
            )
        }
    }

    fun next(auto: Boolean = false) {
        val current = _state.value
        if (current.queue.isEmpty()) return
        if (current.queue.size == 1) {
            when {
                auto && playMode == DesktopPlayMode.REPEAT_ONE -> start(current.queue, 0)
                auto && playMode == DesktopPlayMode.REPEAT_ALL -> start(current.queue, 0)
                auto -> _state.update { it.copy(playing = false, paused = false) }
                else -> start(current.queue, 0)
            }
            return
        }
        if (playMode == DesktopPlayMode.SHUFFLE) {
            val nextIndex = (current.queue.indices)
                .filter { it != current.index }
                .randomOrNull() ?: 0
            start(current.queue, nextIndex)
            return
        }
        val nextIndex = current.index + 1
        if (nextIndex > current.queue.lastIndex) {
            if (auto && playMode == DesktopPlayMode.SEQUENTIAL) {
                _state.update { it.copy(playing = false, paused = false) }
                return
            }
            start(current.queue, 0)
        } else {
            start(current.queue, nextIndex)
        }
    }

    fun cyclePlayMode() {
        playMode = when (playMode) {
            DesktopPlayMode.SEQUENTIAL -> DesktopPlayMode.REPEAT_ALL
            DesktopPlayMode.REPEAT_ALL -> DesktopPlayMode.REPEAT_ONE
            DesktopPlayMode.REPEAT_ONE -> DesktopPlayMode.SHUFFLE
            DesktopPlayMode.SHUFFLE -> DesktopPlayMode.SEQUENTIAL
        }
        _state.update { it.copy(playMode = playMode) }
    }

    fun previous() {
        val current = _state.value
        if (current.queue.isEmpty()) return
        val previousIndex = if (current.index - 1 < 0) {
            current.queue.lastIndex
        } else {
            current.index - 1
        }
        start(current.queue, previousIndex)
    }

    fun playAt(index: Int) {
        val current = _state.value
        if (index in current.queue.indices) start(current.queue, index)
    }

    /** Reopens the stream and skips MP3 frames up to [positionMs]. */
    fun seekTo(positionMs: Long) {
        val current = _state.value
        val track = current.track ?: return
        if (current.loading) return
        val target = if (current.durationMs > 0) {
            positionMs.coerceIn(0L, (current.durationMs - 1_000L).coerceAtLeast(0L))
        } else {
            positionMs.coerceAtLeast(0L)
        }
        start(
            queue = current.queue.ifEmpty { listOf(track) },
            index = current.index.coerceAtLeast(0),
            startPositionMs = target,
        )
    }

    fun shutdown() {
        stopCurrent()
    }

    private fun start(
        queue: List<Track>,
        index: Int,
        startPositionMs: Long = 0L,
        localFile: File? = null,
    ) {
        val track = queue.getOrNull(index) ?: return
        stopCurrent()
        val currentGeneration = ++generation
        stopped = false
        paused = false
        _state.value = DesktopPlaybackState(
            track = track,
            queue = queue,
            index = index,
            loading = true,
            positionMs = startPositionMs,
            durationMs = track.durationMs ?: 0L,
            playMode = playMode,
        )
        job = scope.launch {
            runPlayback(currentGeneration, track, startPositionMs, localFile)
        }
    }

    private suspend fun runPlayback(
        currentGeneration: Long,
        track: Track,
        startPositionMs: Long,
        localFile: File?,
    ) {
        try {
            if (localFile != null) {
                FileInputStream(localFile).use { stream ->
                    input = stream
                    playStream(currentGeneration, track, stream, startPositionMs)
                }
            } else {
                val stream = repository.resolveStream(track)
                val request = Request.Builder()
                    .url(stream.url)
                    .header("User-Agent", JsonHttpClient.USER_AGENT)
                    .build()
                httpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        throw MusicException.Server(response.code)
                    }
                    val body = response.body.byteStream()
                    input = body
                    playStream(currentGeneration, track, body, startPositionMs)
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            if (stopped || currentGeneration != generation) return
            _state.update {
                it.copy(
                    loading = false,
                    playing = false,
                    paused = false,
                    error = error.userMessage(),
                )
            }
        } finally {
            if (currentGeneration == generation) {
                line?.let { runCatching { it.close() } }
                line = null
                input = null
            }
        }
    }

    private suspend fun playStream(
        currentGeneration: Long,
        track: Track,
        stream: InputStream,
        startPositionMs: Long,
    ) {
        val bitstream = Bitstream(stream)
        val decoder = Decoder()
        var output: SourceDataLine? = null
        var positionMs = 0L
        var lastReport = 0L
        try {
            while (currentCoroutineContext().isActive && !stopped && currentGeneration == generation) {
                if (paused) {
                    output?.let {
                        runCatching { it.stop() }
                        runCatching { it.flush() }
                    }
                    while (paused && !stopped && currentGeneration == generation) {
                        delay(40)
                    }
                    if (stopped || currentGeneration != generation) break
                    output?.let { runCatching { it.start() } }
                }

                val header = bitstream.readFrame() ?: break
                positionMs += header.ms_per_frame().toLong()
                if (positionMs < startPositionMs) {
                    // Fast seek: consume frame headers without decoding while no
                    // audio device is open yet.
                    bitstream.closeFrame()
                    continue
                }
                val sample = decoder.decodeFrame(header, bitstream) as SampleBuffer
                if (output == null) {
                    val format = AudioFormat(
                        decoder.getOutputFrequency().toFloat(),
                        16,
                        decoder.getOutputChannels(),
                        true,
                        false,
                    )
                    val created = AudioSystem.getSourceDataLine(format)
                    created.open(format, maxOf(decoder.getOutputBlockSize() * 4, 8_192))
                    created.start()
                    output = created
                    line = created
                    _state.update {
                        it.copy(
                            loading = false,
                            playing = true,
                            paused = false,
                            positionMs = positionMs,
                            durationMs = track.durationMs ?: it.durationMs,
                        )
                    }
                }
                val samples = sample.buffer
                val pcm = ByteArray(sample.bufferLength * 2)
                for (index in 0 until sample.bufferLength) {
                    val value = samples[index].toInt()
                    pcm[index * 2] = (value and 0xFF).toByte()
                    pcm[index * 2 + 1] = ((value shr 8) and 0xFF).toByte()
                }
                output.write(pcm, 0, pcm.size)
                bitstream.closeFrame()
                val now = System.currentTimeMillis()
                if (now - lastReport >= 250L) {
                    lastReport = now
                    _state.update {
                        it.copy(
                            positionMs = positionMs,
                            durationMs = it.durationMs.takeIf { duration -> duration > 0 }
                                ?: positionMs,
                        )
                    }
                }
            }
            output?.let {
                runCatching { it.drain() }
                runCatching { it.stop() }
            }
            if (!stopped && currentGeneration == generation) {
                when (playMode) {
                    DesktopPlayMode.REPEAT_ONE -> {
                        start(queue = _state.value.queue, index = _state.value.index)
                    }

                    else -> {
                        _state.update {
                            it.copy(
                                loading = false,
                                playing = false,
                                paused = false,
                                positionMs = if (it.durationMs > 0) it.durationMs else positionMs,
                            )
                        }
                        next(auto = true)
                    }
                }
            }
        } finally {
            runCatching { bitstream.close() }
            output?.let { runCatching { it.close() } }
            if (line === output) line = null
        }
    }

    private fun stopCurrent() {
        stopped = true
        paused = false
        generation++
        job?.cancel()
        job = null
        runCatching { input?.close() }
        runCatching { line?.stop() }
        runCatching { line?.flush() }
        runCatching { line?.close() }
        input = null
        line = null
    }
}
