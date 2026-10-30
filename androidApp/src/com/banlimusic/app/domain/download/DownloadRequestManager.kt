/**
 * 文件作用：统一处理下载请求，检测音质冲突并暂存等待用户确认的批次。
 */
package com.banlimusic.app.domain.download

import com.banlimusic.app.core.model.DownloadQueueOutcome
import com.banlimusic.app.core.model.DownloadQualityConflict
import com.banlimusic.app.core.model.DownloadQualityDecision
import com.banlimusic.app.core.model.DownloadRequestItem
import com.banlimusic.app.core.model.Track
import com.banlimusic.app.data.repository.DownloadRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Conflicts waiting for the global download-quality dialog. */
data class PendingDownloadPrompt(
    val conflicts: List<DownloadQualityConflict>,
)

/**
 * Every download entry point (search, library, player, cached-track conversion)
 * goes through this manager so a batch only asks once and the chosen policy is
 * applied to all conflicting tracks.
 */
class DownloadRequestManager(
    private val downloads: DownloadRepository,
    private val scope: CoroutineScope,
) {
    private val _prompt = MutableStateFlow<PendingDownloadPrompt?>(null)
    val prompt: StateFlow<PendingDownloadPrompt?> = _prompt.asStateFlow()

    private var pending: PendingRequest? = null

    fun download(
        tracks: Collection<Track>,
        qualityKbps: Int? = null,
        downloadLyrics: Boolean? = null,
        downloadArtwork: Boolean? = null,
        onOutcome: (DownloadQueueOutcome) -> Unit = {},
    ) {
        val distinct = tracks.distinctBy { it.id }
        if (distinct.isEmpty()) {
            onOutcome(DownloadQueueOutcome())
            return
        }
        scope.launch {
            val quality = runCatching { downloads.resolveRequestedQuality(qualityKbps) }
                .getOrDefault(128)
            val items = distinct.map { DownloadRequestItem(it, quality) }
            request(items, onOutcome) { decision ->
                downloads.enqueueAll(
                    items = items,
                    decision = decision,
                    downloadLyrics = downloadLyrics,
                    downloadArtwork = downloadArtwork,
                )
            }
        }
    }

    /**
     * Runs [executor] immediately when there is no conflict; otherwise stores it
     * until [resolve] receives the user's decision.
     */
    fun request(
        items: List<DownloadRequestItem>,
        onOutcome: (DownloadQueueOutcome) -> Unit = {},
        executor: suspend (DownloadQualityDecision) -> DownloadQueueOutcome,
    ) {
        val distinct = items.distinctBy { it.track.id }
            .filter { it.track.sources.isNotEmpty() }
        if (distinct.isEmpty()) {
            onOutcome(DownloadQueueOutcome())
            return
        }
        scope.launch {
            val conflicts = runCatching { downloads.findConflicts(distinct) }
                .getOrDefault(emptyList())
            if (conflicts.isEmpty()) {
                onOutcome(execute(executor, DownloadQualityDecision.COEXIST, distinct.size))
            } else {
                pending = PendingRequest(
                    items = distinct,
                    executor = executor,
                    onOutcome = onOutcome,
                )
                _prompt.value = PendingDownloadPrompt(conflicts)
            }
        }
    }

    fun resolve(decision: DownloadQualityDecision) {
        val request = pending ?: return
        pending = null
        _prompt.value = null
        scope.launch {
            request.onOutcome(execute(request.executor, decision, request.items.size))
        }
    }

    fun dismiss() = resolve(DownloadQualityDecision.SKIP)

    private suspend fun execute(
        executor: suspend (DownloadQualityDecision) -> DownloadQueueOutcome,
        decision: DownloadQualityDecision,
        count: Int,
    ): DownloadQueueOutcome = try {
        executor(decision)
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Throwable) {
        DownloadQueueOutcome(failed = count)
    }

    private data class PendingRequest(
        val items: List<DownloadRequestItem>,
        val executor: suspend (DownloadQualityDecision) -> DownloadQueueOutcome,
        val onOutcome: (DownloadQueueOutcome) -> Unit,
    )
}
