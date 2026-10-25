package com.banlimusic.app.data.sync

/** Failures raised by the OneDrive/Graph sync stack. */
sealed class SyncException(message: String, cause: Throwable? = null) : Exception(message, cause) {

    class NotConfigured : SyncException("尚未配置 OneDrive 应用 ID")

    class NotSignedIn : SyncException("请先登录 OneDrive")

    class AuthRequired : SyncException("登录已失效，请重新登录 OneDrive")

    class Network(cause: Throwable? = null) : SyncException("网络连接失败，请检查网络", cause)

    class Timeout(cause: Throwable? = null) : SyncException("连接 OneDrive 超时，请稍后重试", cause)

    class RateLimited(val retryAfterMs: Long) : SyncException("OneDrive 请求过于频繁，请稍后重试")

    class Conflict : SyncException("云端数据已被其他设备修改")

    class NotFound : SyncException("云端同步文件不存在")

    class Server(val code: Int) : SyncException("OneDrive 服务暂不可用（$code）")

    class Parse(cause: Throwable? = null) : SyncException("云端同步数据无法解析", cause)

    class Unavailable(message: String) : SyncException(message)
}

/** User-facing Chinese message, never exposes raw HTTP details. */
fun Throwable.syncUserMessage(): String = when (this) {
    is SyncException.RateLimited -> {
        val seconds = (retryAfterMs / 1000).coerceAtLeast(1)
        "OneDrive 请求过于频繁，请约 ${seconds}秒后再试"
    }

    is SyncException -> message ?: "同步失败"
    is java.io.IOException -> "网络连接失败，请检查网络"
    else -> message ?: "同步失败"
}
