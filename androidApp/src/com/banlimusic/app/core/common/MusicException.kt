package com.banlimusic.app.core.common

/** Domain-level errors. Providers map transport/API failures onto this type. */
sealed class MusicException(message: String, cause: Throwable? = null) : Exception(message, cause) {

    class Network(cause: Throwable? = null) : MusicException("网络连接失败，请检查网络", cause)

    class Timeout(cause: Throwable? = null) : MusicException("请求超时，请稍后重试", cause)

    class RateLimited(val retryAfterMs: Long) : MusicException("请求过于频繁")

    class NotFound : MusicException("内容不存在或已下架")

    class Unauthorized : MusicException("没有访问权限")

    class Server(val code: Int) : MusicException("音乐服务暂不可用（$code）")

    class Parse(cause: Throwable? = null) : MusicException("返回数据无法解析", cause)

    class Unavailable(message: String = "当前音乐源暂不可用") : MusicException(message)

    class NoPlayableSource : MusicException("没有可用的播放来源")
}

fun Throwable.userMessage(): String = when (this) {
    is MusicException.RateLimited -> {
        val seconds = (retryAfterMs / 1000).coerceAtLeast(1)
        "请求过于频繁，请约 ${formatDuration(seconds)}后再试"
    }

    is MusicException -> message ?: "发生未知错误"
    else -> message ?: "发生未知错误"
}

fun formatDuration(seconds: Long): String {
    if (seconds < 60) return "${seconds}秒"
    val minutes = seconds / 60
    if (minutes < 60) return "${minutes}分钟"
    val hours = minutes / 60
    val rest = minutes % 60
    return if (rest == 0L) "${hours}小时" else "${hours}小时${rest}分钟"
}
