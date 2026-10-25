package com.banlimusic.app.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class QualityTiersTest {

    @Test
    fun normalizeAlwaysReturnsOneOfFourPresets() {
        assertEquals(listOf(128, 192, 320, 740), QualityTiers.PRESET_KBPS)
        assertEquals(128, QualityTiers.normalize(64))
        assertEquals(128, QualityTiers.normalize(128))
        assertEquals(192, QualityTiers.normalize(200))
        assertEquals(320, QualityTiers.normalize(320))
        assertEquals(740, QualityTiers.normalize(700))
        assertEquals(740, QualityTiers.normalize(999))
        assertTrue(QualityTiers.PRESET_KBPS.all(QualityTiers::isPreset))
    }

    @Test
    fun providersWithFewerTiersMapToClosestQuality() {
        val provider = listOf(128, 320)
        assertEquals(128, QualityTiers.mapToProvider(128, provider))
        assertEquals(128, QualityTiers.mapToProvider(192, provider))
        assertEquals(320, QualityTiers.mapToProvider(320, provider))
        assertEquals(320, QualityTiers.mapToProvider(740, provider))
        assertEquals(320, QualityTiers.mapToProvider(999, provider))
    }

    @Test
    fun providersWithMoreTiersExtractClosestValues() {
        val provider = listOf(64, 128, 192, 256, 320, 740, 999)
        assertEquals(128, QualityTiers.mapToProvider(128, provider))
        assertEquals(192, QualityTiers.mapToProvider(192, provider))
        assertEquals(320, QualityTiers.mapToProvider(320, provider))
        assertEquals(740, QualityTiers.mapToProvider(740, provider))
        // 999 is no longer a preset; it normalizes to the lossless tier.
        assertEquals(740, QualityTiers.mapToProvider(999, provider))
    }

    @Test
    fun providerWithoutQualityListUsesThePreset() {
        assertEquals(320, QualityTiers.mapToProvider(320, emptyList()))
        assertEquals(740, QualityTiers.mapToProvider(760, emptyList()))
    }

    @Test
    fun projectToPresetsAlwaysProducesFourEntries() {
        assertEquals(
            listOf(128, 128, 320, 320),
            QualityTiers.projectToPresets(listOf(128, 320)),
        )
        assertEquals(QualityTiers.PRESET_KBPS, QualityTiers.projectToPresets(emptyList()))
    }
}
