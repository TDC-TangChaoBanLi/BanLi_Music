package com.banlimusic.app.core.limiter

import com.banlimusic.app.core.common.MusicException
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlin.math.max

fun interface TimeSource {
    fun nowMs(): Long
}

object SystemTimeSource : TimeSource {
    override fun nowMs(): Long = System.nanoTime() / 1_000_000L
}

data class RateLimitStatus(
    val limit: Int,
    val used: Int,
    val remaining: Int,
    val windowResetInMs: Long,
    val cooldownRemainingMs: Long,
)

/**
 * Sliding-window limiter shared by every request of one backend. Waits for short
 * windows, but fails fast with a user-visible error when the wait would be long.
 */
class SlidingWindowRateLimiter(
    private val limit: Int,
    private val windowMs: Long,
    private val timeSource: TimeSource = SystemTimeSource,
    private val maxWaitMs: Long = 4_000L,
) {
    private val mutex = Mutex()
    private val timestamps = ArrayDeque<Long>()
    private var cooldownUntil = 0L

    suspend fun acquire() {
        while (true) {
            val waitMs = mutex.withLock {
                val now = timeSource.nowMs()
                val cooldown = cooldownUntil - now
                if (cooldown > 0) {
                    cooldown
                } else {
                    while (timestamps.isNotEmpty() && now - timestamps.first() >= windowMs) {
                        timestamps.removeFirst()
                    }
                    if (timestamps.size < limit) {
                        timestamps.addLast(now)
                        0L
                    } else {
                        timestamps.first() + windowMs - now
                    }
                }
            }
            if (waitMs <= 0L) return
            if (waitMs > maxWaitMs) throw MusicException.RateLimited(waitMs)
            delay(waitMs)
        }
    }

    suspend fun penalize(retryAfterMs: Long) {
        val retry = retryAfterMs.coerceIn(1_000L, windowMs)
        mutex.withLock {
            cooldownUntil = max(cooldownUntil, timeSource.nowMs() + retry)
        }
    }

    suspend fun status(): RateLimitStatus = mutex.withLock {
        val now = timeSource.nowMs()
        while (timestamps.isNotEmpty() && now - timestamps.first() >= windowMs) {
            timestamps.removeFirst()
        }
        RateLimitStatus(
            limit = limit,
            used = timestamps.size,
            remaining = (limit - timestamps.size).coerceAtLeast(0),
            windowResetInMs = timestamps.firstOrNull()?.let { it + windowMs - now }?.coerceAtLeast(0) ?: 0L,
            cooldownRemainingMs = (cooldownUntil - now).coerceAtLeast(0L),
        )
    }
}

/** Bounds concurrency, then asks the limiter for a slot. */
class RequestScheduler(
    maxConcurrent: Int,
    private val limiter: SlidingWindowRateLimiter,
) {
    private val semaphore = Semaphore(maxConcurrent)

    suspend fun <T> execute(block: suspend () -> T): T = semaphore.withPermit {
        limiter.acquire()
        block()
    }

    suspend fun penalize(retryAfterMs: Long) = limiter.penalize(retryAfterMs)

    suspend fun status(): RateLimitStatus = limiter.status()
}
