package com.banlimusic.app.core.limiter

import com.banlimusic.app.core.common.MusicException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class SlidingWindowRateLimiterTest {

    private class FakeTime(var now: Long = 0L) : TimeSource {
        override fun nowMs(): Long = now
    }

    @Test
    fun throwsInsteadOfWaitingWhenWindowIsFarAway() = runTest {
        val clock = FakeTime()
        val limiter = SlidingWindowRateLimiter(
            limit = 2,
            windowMs = 1_000L,
            timeSource = clock,
            maxWaitMs = 100L,
        )

        limiter.acquire()
        limiter.acquire()

        assertThrows(MusicException.RateLimited::class.java) {
            kotlinx.coroutines.runBlocking { limiter.acquire() }
        }
    }

    @Test
    fun releasesSlotsAfterWindowPasses() = runTest {
        val clock = FakeTime()
        val limiter = SlidingWindowRateLimiter(
            limit = 1,
            windowMs = 1_000L,
            timeSource = clock,
            maxWaitMs = 100L,
        )

        limiter.acquire()
        clock.now = 1_001L
        limiter.acquire()

        val status = limiter.status()
        assertEquals(1, status.used)
        assertEquals(0, status.remaining)
    }

    @Test
    fun penalizeBlocksUntilCooldownExpires() = runTest {
        val clock = FakeTime()
        val limiter = SlidingWindowRateLimiter(
            limit = 10,
            windowMs = 10_000L,
            timeSource = clock,
            maxWaitMs = 100L,
        )

        limiter.acquire()
        limiter.penalize(1_000L)
        assertThrows(MusicException.RateLimited::class.java) {
            kotlinx.coroutines.runBlocking { limiter.acquire() }
        }

        clock.now = 1_001L
        limiter.acquire()
        assertEquals(0L, limiter.status().cooldownRemainingMs)
    }

    @Test
    fun statusReflectsUsedSlots() = runTest {
        val clock = FakeTime()
        val limiter = SlidingWindowRateLimiter(5, 5_000L, clock)

        limiter.acquire()
        limiter.acquire()

        val status = limiter.status()
        assertEquals(5, status.limit)
        assertEquals(2, status.used)
        assertEquals(3, status.remaining)
        assertEquals(5_000L, status.windowResetInMs)
    }

    @Test
    fun enforcesMinimumIntervalBetweenRequests() = runTest {
        val limiter = SlidingWindowRateLimiter(
            limit = 10,
            windowMs = 10_000L,
            timeSource = TimeSource { testScheduler.currentTime },
            maxWaitMs = 5_000L,
            minIntervalMs = 1_000L,
        )

        limiter.acquire()
        assertEquals(0L, testScheduler.currentTime)
        limiter.acquire()
        assertEquals(1_000L, testScheduler.currentTime)
    }

    @Test
    fun readsLimitProviderAtRuntimeAndCapsAtHardLimit() = runTest {
        var configuredLimit = 2
        val limiter = SlidingWindowRateLimiter(
            limit = 50,
            windowMs = 10_000L,
            timeSource = FakeTime(),
            maxWaitMs = 100L,
            limitProvider = { configuredLimit },
        )

        limiter.acquire()
        limiter.acquire()
        assertEquals(2, limiter.status().limit)
        assertEquals(0, limiter.status().remaining)

        configuredLimit = 100
        assertEquals(50, limiter.status().limit)
    }
}
