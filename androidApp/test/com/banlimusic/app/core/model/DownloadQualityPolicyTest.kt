/**
 * 文件作用：验证下载音质冲突策略在各选项下的行为。
 */
package com.banlimusic.app.core.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadQualityPolicyTest {

    @Test
    fun `no existing version always downloads`() {
        DownloadQualityDecision.entries.forEach { decision ->
            val plan = DownloadQualityPolicy.plan(decision, emptyList(), 320)
            assertTrue(plan.download)
            assertFalse(plan.deleteExisting)
        }
    }

    @Test
    fun `same quality is a no-op`() {
        DownloadQualityDecision.entries.forEach { decision ->
            val plan = DownloadQualityPolicy.plan(decision, listOf(320), 320)
            assertFalse(plan.download)
            assertFalse(plan.deleteExisting)
        }
    }

    @Test
    fun `keep highest skips when existing is higher`() {
        val plan = DownloadQualityPolicy.plan(
            DownloadQualityDecision.KEEP_HIGHEST,
            listOf(740),
            320,
        )
        assertFalse(plan.download)
    }

    @Test
    fun `keep highest replaces when requested is higher`() {
        val plan = DownloadQualityPolicy.plan(
            DownloadQualityDecision.KEEP_HIGHEST,
            listOf(128, 192),
            320,
        )
        assertTrue(plan.download)
        assertTrue(plan.deleteExisting)
    }

    @Test
    fun `replace deletes existing versions`() {
        val plan = DownloadQualityPolicy.plan(
            DownloadQualityDecision.REPLACE,
            listOf(320),
            128,
        )
        assertTrue(plan.download)
        assertTrue(plan.deleteExisting)
    }

    @Test
    fun `coexist keeps existing versions`() {
        val plan = DownloadQualityPolicy.plan(
            DownloadQualityDecision.COEXIST,
            listOf(320),
            128,
        )
        assertTrue(plan.download)
        assertFalse(plan.deleteExisting)
    }

    @Test
    fun `skip leaves existing versions untouched`() {
        val plan = DownloadQualityPolicy.plan(
            DownloadQualityDecision.SKIP,
            listOf(128),
            320,
        )
        assertFalse(plan.download)
        assertFalse(plan.deleteExisting)
    }
}
