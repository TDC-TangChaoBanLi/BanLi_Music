package com.banlimusic.desktop.player

import com.banlimusic.app.core.common.MusicException
import com.banlimusic.app.core.common.userMessage
import com.banlimusic.app.core.model.Track
import com.banlimusic.app.core.network.JsonHttpClient
import com.banlimusic.desktop.data.DesktopMusicRepository
import com.banlimusic.desktop.data.DesktopPlaybackCache
import com.banlimusic.desktop.data.DesktopSettingsRepository
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
import java.net.URI
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.FloatControl
import javax.sound.sampled.SourceDataLine
import kotlin.math.log10

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
    val volume: Float = 1f,
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
    private val settings: DesktopSettingsRepository,
    private val cache: DesktopPlaybackCache,
) {
    private val _state = MutableStateFlow(DesktopPlaybackState())
    val state: StateFlow<DesktopPlaybackState> = _state.asStateFlow()

    private var job: Job? = null
    private var generation = 0L
    @Volatile private var stopped = true
    @Volatile private var paused = false
    @Volatile private var playMode = DesktopPlayMode.SEQUENTIAL
    @Volatile private var volume = 1f
    @Volatile private var input: InputStream? = null
    @Volatile private var line: SourceDataLine? = null
    @Volatile private var localFiles: Map<String, File> = emptyMap()

    fun play(track: Track, queue: List<Track> = listOf(track)) {
        localFiles = emptyMap()
        val effectiveQueue = queue.ifEmpty { listOf(track) }
        val index = effectiveQueue.indexOfFirst { it.id == track.id }
            .takeIf { it >= 0 }
            ?: 0
        start(effectiveQueue, index)
    }

    fun playLocal(track: Track, file: File) {
        if (!file.isFile) return
        localFiles = mapOf(track.id to file)
        start(queue = listOf(track), index = 0, localFile = file)
    }

    fun playLocalQueue(track: Track, queue: List<Track>, files: Map<String, File>) {
        if (queue.isEmpty()) return
        localFiles = files
        val index = queue.indexOfFirst { it.id == track.id }.takeIf { it >= 0 } ?: 0
        start(queue = queue, index = index, localFile = files[track.id])
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

    fun setVolume(value: Float) {
        volume = value.coerceIn(0f, 1f)
        _state.update { it.copy(volume = volume) }
        line?.let(::applyVolume)
    }

    private fun applyVolume(line: SourceDataLine) {
        runCatching {
            val control = runCatching {
                line.getControl(FloatControl.Type.MASTER_GAIN) as FloatControl
            }.getOrElse {
                line.getControl(FloatControl.Type.VOLUME) as FloatControl
            }
            val target = if (control.type == FloatControl.Type.VOLUME) {
                volume
            } else if (volume <= 0.001f) {
                control.minimum
            } else {
                (20.0 * log10(volume.toDouble())).toFloat()
            }
            control.value = target.coerceIn(control.minimum, control.maximum)
        }
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
        val effectiveLocalFile = localFile ?: localFiles[track.id]
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
            durationMs = track.durationMs?.takeIf { it > 0L }
                ?: track.sources.firstNotNullOfOrNull { source ->
                    source.durationMs?.takeIf { it > 0L }
                }
                ?: 0L,
            playMode = playMode,
            volume = volume,
        )
        job = scope.launch {
            runPlayback(currentGeneration, track, startPositionMs, effectiveLocalFile)
        }
    }

    private suspend fun runPlayback(
        currentGeneration: Long,
        track: Track,
        startPositionMs: Long,
        localFile: File?,
    ) {
        val qualityKbps = settings.current().qualityKbps
        var cacheSink: DesktopPlaybackCache.CacheSink? = null
        try {
            val cachedFile = localFile ?: cache.cachedFile(track.id, qualityKbps)
            if (cachedFile != null) {
                updateDuration(localMp3DurationMs(cachedFile))
                val completed = FileInputStream(cachedFile).use { stream ->
                    input = stream
                    playStream(currentGeneration, track, stream, startPositionMs)
                }
                if (completed) handleTrackEnd(currentGeneration)
            } else {
                val stream = repository.resolveStream(track)
                updateDuration(estimateDurationMs(stream.sizeBytes, stream.bitrateKbps))
                val request = Request.Builder()
                    .url(stream.url)
                    .header("User-Agent", JsonHttpClient.USER_AGENT)
                    .build()
                val completed = httpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        throw MusicException.Server(response.code)
                    }
                    if (_state.value.durationMs <= 0L) {
                        updateDuration(
                            estimateDurationMs(
                                response.body.contentLength().takeIf { it > 0L },
                                stream.bitrateKbps,
                            ),
                        )
                    }
                    val body = response.body.byteStream()
                    val sink = if (startPositionMs == 0L) {
                        runCatching {
                            cache.begin(
                                track = track,
                                qualityKbps = qualityKbps,
                                extension = extensionFor(stream.url),
                                expectedBytes = stream.sizeBytes ?: response.body.contentLength(),
                            )
                        }.getOrNull()
                    } else {
                        null
                    }
                    cacheSink = sink
                    val playbackStream = if (sink != null) {
                        TeeInputStream(body, sink)
                    } else {
                        body
                    }
                    input = playbackStream
                    val done = playStream(
                        currentGeneration,
                        track,
                        playbackStream,
                        startPositionMs,
                    )
                    cacheSink?.let { active ->
                        if (done && !active.failed) {
                            // Commit before starting the next track: start() cancels
                            // this job, and a suspended commit would be cancelled and
                            // the completed cache file deleted.
                            cache.commit(active)
                        } else {
                            cache.abort(active)
                        }
                        cacheSink = null
                    }
                    done
                }
                if (completed) handleTrackEnd(currentGeneration)
            }
        } catch (cancelled: CancellationException) {
            cacheSink?.let(cache::abort)
            throw cancelled
        } catch (error: Throwable) {
            cacheSink?.let(cache::abort)
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
    ): Boolean {
        val bitstream = Bitstream(stream)
        val decoder = Decoder()
        var output: SourceDataLine? = null
        var positionMs = 0L
        var lastReport = 0L
        var reachedEnd = false
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

                val header = bitstream.readFrame()
                if (header == null) {
                    reachedEnd = true
                    break
                }
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
                    applyVolume(created)
                    created.start()
                    output = created
                    line = created
                    _state.update {
                        it.copy(
                            loading = false,
                            playing = true,
                            paused = false,
                            positionMs = positionMs,
                            durationMs = it.durationMs,
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
                                ?: 0L,
                        )
                    }
                }
            }
            output?.let {
                runCatching { it.drain() }
                runCatching { it.stop() }
            }
            if (!stopped && currentGeneration == generation) {
                _state.update {
                    it.copy(
                        loading = false,
                        playing = false,
                        paused = false,
                        positionMs = if (it.durationMs > 0) it.durationMs else positionMs,
                    )
                }
            }
        } finally {
            runCatching { bitstream.close() }
            output?.let { runCatching { it.close() } }
            if (line === output) line = null
        }
        return reachedEnd
    }

    /** Continues with the next track only after the current cache was committed. */
    private fun handleTrackEnd(currentGeneration: Long) {
        if (stopped || currentGeneration != generation) return
        when (playMode) {
            DesktopPlayMode.REPEAT_ONE -> {
                start(queue = _state.value.queue, index = _state.value.index)
            }

            else -> next(auto = true)
        }
    }

    private fun updateDuration(durationMs: Long) {
        if (durationMs <= 0L) return
        _state.update { it.copy(durationMs = durationMs) }
    }

    /** Estimates CBR duration from the provider's size/bitrate metadata. */
    private fun estimateDurationMs(sizeBytes: Long?, bitrateKbps: Int?): Long {
        val size = sizeBytes?.takeIf { it > 0L } ?: return 0L
        val bitrate = bitrateKbps?.takeIf { it > 0 } ?: return 0L
        // bytes * 8 = bits; bitrate is kbit/s. bits / kbit/s = milliseconds,
        // so no additional *1000 is needed here.
        return (size * 8L / bitrate).coerceIn(1L, MAX_ESTIMATED_DURATION_MS)
    }

    private fun extensionFor(url: String): String {
        val path = runCatching { URI(url).path }.getOrNull().orEmpty().lowercase()
        val extension = path.substringAfterLast('.', "")
        return if (extension in AUDIO_EXTENSIONS) extension else "mp3"
    }

    /** Scans MP3 frame headers for an exact duration; non-MP3 files return 0. */
    private fun localMp3DurationMs(file: File): Long {
        if (!file.name.substringAfterLast('.', "").equals("mp3", ignoreCase = true)) {
            return 0L
        }
        return runCatching {
            FileInputStream(file).use { stream ->
                val bitstream = Bitstream(stream)
                var total = 0L
                try {
                    while (true) {
                        val header = bitstream.readFrame() ?: break
                        total += header.ms_per_frame().toLong()
                        bitstream.closeFrame()
                    }
                } finally {
                    runCatching { bitstream.close() }
                }
                total
            }
        }.getOrDefault(0L)
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

    private class TeeInputStream(
        private val input: InputStream,
        private val sink: DesktopPlaybackCache.CacheSink,
    ) : InputStream() {
        override fun read(): Int {
            val value = input.read()
            if (value >= 0) write { sink.output.write(value) }
            return value
        }

        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            val read = input.read(buffer, offset, length)
            if (read > 0) write { sink.output.write(buffer, offset, read) }
            return read
        }

        override fun close() {
            input.close()
        }

        private inline fun write(block: () -> Unit) {
            runCatching(block).onFailure { sink.failed = true }
        }
    }

    private companion object {
        const val MAX_ESTIMATED_DURATION_MS = 24L * 60L * 60L * 1_000L
        val AUDIO_EXTENSIONS = setOf("mp3", "flac", "m4a", "aac", "ogg", "opus", "wav")
    }
}
