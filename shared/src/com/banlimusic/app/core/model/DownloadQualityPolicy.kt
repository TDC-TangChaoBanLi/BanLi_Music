/**
 * 文件作用：定义下载音质冲突的处理策略、请求模型与队列结果。
 */
package com.banlimusic.app.core.model

/** User choice when a track already exists at another quality. */
enum class DownloadQualityDecision {
    /** Keep whichever version has the higher bitrate. */
    KEEP_HIGHEST,

    /** Delete the existing version and download the requested quality. */
    REPLACE,

    /** Keep both files; local lists show only the highest quality. */
    COEXIST,

    /** Keep the existing file and do not download anything. */
    SKIP,
}

/** One track with the quality requested for this download. */
data class DownloadRequestItem(
    val track: Track,
    val qualityKbps: Int,
)

/** A completed download whose quality differs from the requested one. */
data class DownloadQualityConflict(
    val track: Track,
    val existingQualities: List<Int>,
    val requestedQualityKbps: Int,
)

/** What the repository must do for one track after applying the user choice. */
data class DownloadVersionPlan(
    val download: Boolean,
    val deleteExisting: Boolean,
)

object DownloadQualityPolicy {

    /**
     * Same-quality requests are a no-op. Otherwise the action decides whether
     * the existing versions are deleted and whether a new download starts.
     */
    fun plan(
        decision: DownloadQualityDecision,
        existingQualities: Collection<Int>,
        requestedQualityKbps: Int,
    ): DownloadVersionPlan {
        val existing = existingQualities.filter { it > 0 }.distinct()
        if (existing.isEmpty()) return DownloadVersionPlan(download = true, deleteExisting = false)
        if (requestedQualityKbps in existing) {
            return DownloadVersionPlan(download = false, deleteExisting = false)
        }
        return when (decision) {
            DownloadQualityDecision.KEEP_HIGHEST -> {
                if (existing.max() >= requestedQualityKbps) {
                    DownloadVersionPlan(download = false, deleteExisting = false)
                } else {
                    DownloadVersionPlan(download = true, deleteExisting = true)
                }
            }

            DownloadQualityDecision.REPLACE -> DownloadVersionPlan(
                download = true,
                deleteExisting = true,
            )

            DownloadQualityDecision.COEXIST -> DownloadVersionPlan(
                download = true,
                deleteExisting = false,
            )

            DownloadQualityDecision.SKIP -> DownloadVersionPlan(
                download = false,
                deleteExisting = false,
            )
        }
    }
}

/** Result of one batch download request. */
data class DownloadQueueOutcome(
    val queued: Int = 0,
    val skipped: Int = 0,
    val replaced: Int = 0,
    val failed: Int = 0,
) {
    fun summary(): String {
        if (queued == 0 && skipped == 0 && failed == 0) return "没有可下载的歌曲"
        return buildString {
            append("已加入下载队列：$queued 首")
            if (replaced > 0) append("，替换 $replaced 首")
            if (skipped > 0) append("，跳过 $skipped 首")
            if (failed > 0) append("，失败 $failed 首")
        }
    }
}
