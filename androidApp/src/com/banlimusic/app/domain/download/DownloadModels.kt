package com.banlimusic.app.domain.download

import com.banlimusic.app.core.model.Track
import java.io.File

sealed interface DownloadTarget {
    data object MediaStoreMusic : DownloadTarget
    data class Directory(val directory: File) : DownloadTarget
}

enum class DownloadStatus {
    QUEUED,
    DOWNLOADING,
    PAUSED,
    VERIFYING,
    COMPLETED,
    FAILED,
    CANCELLED,
}

data class DownloadTask(
    val id: String,
    val track: Track,
    val status: DownloadStatus,
    val bytesDownloaded: Long,
    val totalBytes: Long,
    val filePath: String?,
    val error: String?,
    val createdAt: Long,
    val updatedAt: Long,
    val qualityKbps: Int = 0,
    val downloadLyrics: Boolean = false,
    val downloadArtwork: Boolean = false,
) {
    val progress: Float
        get() = when {
            status == DownloadStatus.COMPLETED -> 1f
            totalBytes <= 0L -> 0f
            else -> (bytesDownloaded.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f)
        }

    /** Null for MediaStore content URIs. */
    val file: File? get() = filePath
        ?.takeIf { !it.startsWith("content://") }
        ?.let(::File)

    /** Optional artwork sidecar written next to the audio file. */
    val localArtwork: File? get() = file?.let { audio ->
        File(audio.parentFile, "${audio.nameWithoutExtension}.jpg")
            .takeIf(File::exists)
    }

    val isMediaStoreUri: Boolean get() = filePath?.startsWith("content://") == true

    val isActive: Boolean
        get() = status == DownloadStatus.QUEUED ||
            status == DownloadStatus.DOWNLOADING ||
            status == DownloadStatus.VERIFYING

    val canPause: Boolean
        get() = status == DownloadStatus.QUEUED || status == DownloadStatus.DOWNLOADING

    val canResume: Boolean
        get() = status == DownloadStatus.PAUSED || status == DownloadStatus.FAILED || status == DownloadStatus.CANCELLED
}

object DownloadStatusLabels {
    fun label(status: DownloadStatus): String = when (status) {
        DownloadStatus.QUEUED -> "等待中"
        DownloadStatus.DOWNLOADING -> "下载中"
        DownloadStatus.PAUSED -> "已暂停"
        DownloadStatus.VERIFYING -> "校验中"
        DownloadStatus.COMPLETED -> "已完成"
        DownloadStatus.FAILED -> "失败"
        DownloadStatus.CANCELLED -> "已取消"
    }
}
