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

private val monotonicStart = kotlin.time.TimeSource.Monotonic.markNow()

object SystemTimeSource : TimeSource {
    override fun nowMs(): Long = monotonicStart.elapsedNow().inWholeMilliseconds
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
    /** Minimum spacing between two successful requests, e.g. 1s for GD Music. */
    private val minIntervalMs: Long = 0L,
    /** Allows the user-configured limit to change at runtime without rebuilding the client. */
    private val limitProvider: suspend () -> Int = { limit },
) {
    private val mutex = Mutex()
    private val timestamps = ArrayDeque<Long>()
    private var cooldownUntil = 0L
    private var lastRequestAt = 0L
    private var hasRequested = false

    suspend fun acquire() {
        val effectiveLimit = effectiveLimit()
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
                    if (timestamps.size >= effectiveLimit) {
                        timestamps.first() + windowMs - now
                    } else {
                        val intervalWait = if (!hasRequested) {
                            0L
                        } else {
                            (lastRequestAt + minIntervalMs - now).coerceAtLeast(0L)
                        }
                        if (intervalWait > 0L) {
                            intervalWait
                        } else {
                            timestamps.addLast(now)
                            lastRequestAt = now
                            hasRequested = true
                            0L
                        }
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

    suspend fun status(): RateLimitStatus {
        val effectiveLimit = effectiveLimit()
        return mutex.withLock {
            val now = timeSource.nowMs()
            while (timestamps.isNotEmpty() && now - timestamps.first() >= windowMs) {
                timestamps.removeFirst()
            }
            RateLimitStatus(
                limit = effectiveLimit,
                used = timestamps.size,
                remaining = (effectiveLimit - timestamps.size).coerceAtLeast(0),
                windowResetInMs = timestamps.firstOrNull()?.let { it + windowMs - now }?.coerceAtLeast(0) ?: 0L,
                cooldownRemainingMs = (cooldownUntil - now).coerceAtLeast(0L),
            )
        }
    }

    private suspend fun effectiveLimit(): Int =
        limitProvider().coerceIn(1, limit)
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
