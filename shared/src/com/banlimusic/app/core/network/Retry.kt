/**
 * 文件作用：为网络请求提供带指数退避的通用重试工具。
 */
package com.banlimusic.app.core.network

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlin.math.min
import kotlin.random.Random

/**
 * Retries [block] with exponential backoff. Cancellation is never swallowed.
 * Callers decide which failures are transient, so API-level errors such as
 * "not found" are not retried.
 */
suspend fun <T> retryTransient(
    maxAttempts: Int = 3,
    initialDelayMs: Long = 400L,
    maxDelayMs: Long = 4_000L,
    shouldRetry: (Throwable) -> Boolean,
    block: suspend (attempt: Int) -> T,
): T {
    val attempts = maxAttempts.coerceAtLeast(1)
    var lastError: Throwable? = null
    for (attempt in 0 until attempts) {
        try {
            return block(attempt)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            lastError = error
            if (attempt == attempts - 1 || !shouldRetry(error)) throw error
            val base = min(initialDelayMs * (1L shl attempt), maxDelayMs)
            val jitter = if (base > 1L) Random.nextLong(0L, base / 3L + 1L) else 0L
            delay(base + jitter)
        }
    }
    throw lastError ?: IllegalStateException("重试失败")
}
