/**
 * 文件作用：验证网络重试工具只重试瞬时错误并保留取消语义。
 */
package com.banlimusic.app.core.network

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class NetworkRetryTest {

    @Test
    fun retriesTransientFailures() = runTest {
        var attempts = 0
        val result = retryTransient(
            maxAttempts = 3,
            initialDelayMs = 1L,
            shouldRetry = { it is IOException },
        ) {
            attempts++
            if (attempts < 3) throw IOException("temporary")
            "ok"
        }
        assertEquals(3, attempts)
        assertEquals("ok", result)
    }

    @Test
    fun stopsAfterConfiguredAttempts() = runTest {
        var attempts = 0
        val error = runCatching {
            retryTransient<String>(
                maxAttempts = 2,
                initialDelayMs = 1L,
                shouldRetry = { true },
            ) {
                attempts++
                throw IOException("still failing")
            }
        }.exceptionOrNull()
        assertEquals(2, attempts)
        assertTrue(error is IOException)
    }

    @Test
    fun doesNotRetryFinalErrors() = runTest {
        var attempts = 0
        val error = runCatching {
            retryTransient<String>(
                maxAttempts = 3,
                initialDelayMs = 1L,
                shouldRetry = { it is IOException },
            ) {
                attempts++
                throw IllegalArgumentException("not retryable")
            }
        }.exceptionOrNull()
        assertEquals(1, attempts)
        assertTrue(error is IllegalArgumentException)
    }

    @Test
    fun cancellationIsNeverRetried() = runTest {
        var attempts = 0
        val error = runCatching {
            retryTransient<String>(
                maxAttempts = 3,
                initialDelayMs = 1L,
                shouldRetry = { true },
            ) {
                attempts++
                throw CancellationException("cancelled")
            }
        }.exceptionOrNull()
        assertEquals(1, attempts)
        assertTrue(error is CancellationException)
    }
}
