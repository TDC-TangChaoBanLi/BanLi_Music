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
        assertTrue(settings.autoSwitchPlaybackSource)
        assertTrue(settings.favoriteCategoryOrder.isEmpty())
        assertTrue(settings.lastNavItem.isEmpty())
        assertNull(settings.customThemeColor)
    }

    @Test
    fun navStartPrefersLastVisitedThenDefaultThenHome() {
        val valid = setOf("home", "search", "library")

        assertEquals("library", resolveNavStart("library", "search", valid))
        assertEquals("search", resolveNavStart("removed-page", "search", valid))
        assertEquals("home", resolveNavStart(null, null, valid))
        assertEquals("home", resolveNavStart("player", "removed", valid))
    }

    @Test
    fun favoriteCategoryOrderKeepsKnownOrderAndAppendsNewFolders() {
        val ordered = orderFavoriteCategories(
            all = listOf("默认收藏", "摇滚", "轻音乐"),
            storedOrder = listOf("轻音乐"),
        )

        assertEquals("轻音乐", ordered.first())
        assertEquals(listOf("摇滚", "默认收藏"), ordered.drop(1))
    }

    @Test
    fun favoriteCategoryOrderIgnoresUnknownAndDuplicateEntries() {
        val ordered = orderFavoriteCategories(
            all = listOf("A", "B"),
            storedOrder = listOf("B", "B", "removed-folder"),
        )

        assertEquals(listOf("B", "A"), ordered)
    }
}
