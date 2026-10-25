package com.banlimusic.app.core.datastore

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsDefaultsTest {

    @Test
    fun navItemsAlwaysContainEveryDestination() {
        val sanitized = sanitizeNavItems(listOf("home", "search", "home", "unknown"))
        assertEquals(
            listOf("home", "search", "library", "downloads", "settings"),
            sanitized,
        )
    }

    @Test
    fun defaultNavItemFallsBackToHome() {
        val items = sanitizeNavItems(listOf("search", "library"))
        assertEquals("home", sanitizeDefaultNavItem("not-a-tab", items))
        assertEquals("search", sanitizeDefaultNavItem("search", items))
    }

    @Test
    fun providerDefaultsAreConservative() {
        val settings = AppSettings()
        assertEquals(setOf("netease"), settings.enabledSources)
        assertEquals(128, settings.qualityKbps)
        assertEquals(45, settings.apiLimitPerWindow)
        assertEquals(20, settings.searchPageSize)
        assertTrue(settings.providers.customSources.isEmpty())
        assertFalse(settings.requestListArtwork)
        assertTrue(settings.autoLoadArtwork)
        assertNull(settings.customThemeColor)
    }
}
