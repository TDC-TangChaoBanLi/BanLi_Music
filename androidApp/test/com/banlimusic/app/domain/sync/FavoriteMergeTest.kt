package com.banlimusic.app.domain.sync

import com.banlimusic.app.core.model.FavoriteSyncDocument
import com.banlimusic.app.core.model.SyncFolder
import com.banlimusic.app.core.model.SyncMembership
import com.banlimusic.app.core.model.SyncTrack
import com.banlimusic.app.core.model.Track
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FavoriteMergeTest {

    private fun track(id: String): Track = Track(id = id, title = id)

    private fun trackRecord(id: String, updatedAt: Long): SyncTrack = SyncTrack(
        trackId = id,
        track = track(id),
        addedAt = updatedAt,
        updatedAt = updatedAt,
    )

    private fun folder(name: String, updatedAt: Long, deletedAt: Long? = null): SyncFolder =
        SyncFolder(
            name = name,
            createdAt = updatedAt,
            updatedAt = updatedAt,
            deletedAt = deletedAt,
        )

    private fun membership(
        trackId: String,
        folder: String,
        updatedAt: Long,
        deletedAt: Long? = null,
    ): SyncMembership = SyncMembership(
        trackId = trackId,
        folder = folder,
        addedAt = updatedAt,
        updatedAt = updatedAt,
        deletedAt = deletedAt,
    )

    private fun document(
        folders: List<SyncFolder> = emptyList(),
        tracks: List<SyncTrack> = emptyList(),
        memberships: List<SyncMembership> = emptyList(),
    ): FavoriteSyncDocument = FavoriteSyncDocument(
        folders = folders,
        tracks = tracks,
        memberships = memberships,
    )

    @Test
    fun mergesDisjointFoldersAndMemberships() {
        val local = document(
            folders = listOf(folder("A", 10L)),
            tracks = listOf(trackRecord("t1", 10L)),
            memberships = listOf(membership("t1", "A", 10L)),
        )
        val remote = document(
            folders = listOf(folder("B", 20L)),
            tracks = listOf(trackRecord("t2", 20L)),
            memberships = listOf(membership("t2", "B", 20L)),
        )

        val result = FavoriteMerge.merge(local, remote, now = 1_000L, deviceId = "device")

        assertEquals(setOf("A", "B"), result.document.folders.map { it.name }.toSet())
        assertEquals(2, result.activeMemberships)
        assertEquals(1, result.addedMemberships)
        assertEquals(1, result.addedFolders)
        assertEquals(0, result.removedMemberships)
    }

    @Test
    fun newerTombstoneRemovesMembership() {
        val local = document(
            folders = listOf(folder("A", 10L)),
            tracks = listOf(trackRecord("t1", 10L)),
            memberships = listOf(membership("t1", "A", 100L)),
        )
        val remote = document(
            folders = listOf(folder("A", 10L)),
            tracks = listOf(trackRecord("t1", 10L)),
            memberships = listOf(membership("t1", "A", 200L, deletedAt = 200L)),
        )

        val result = FavoriteMerge.merge(local, remote, now = 1_000L, deviceId = "device")

        assertEquals(0, result.activeMemberships)
        assertEquals(1, result.removedMemberships)
        assertEquals(200L, result.document.memberships.single().deletedAt)
    }

    @Test
    fun newerMembershipResurrectsDeletedFolder() {
        val local = document(
            folders = listOf(folder("A", 10L)),
            tracks = listOf(trackRecord("t1", 10L)),
            memberships = listOf(membership("t1", "A", 300L)),
        )
        val remote = document(
            folders = listOf(folder("A", 200L, deletedAt = 200L)),
            tracks = listOf(trackRecord("t1", 10L)),
            memberships = listOf(membership("t1", "A", 200L, deletedAt = 200L)),
        )

        val result = FavoriteMerge.merge(local, remote, now = 1_000L, deviceId = "device")

        assertNull(result.document.folders.single().deletedAt)
        assertEquals(1, result.activeMemberships)
    }

    @Test
    fun equalTimestampsPreferNonDeletedRecord() {
        val local = document(
            folders = listOf(folder("A", 10L)),
            tracks = listOf(trackRecord("t1", 10L)),
            memberships = listOf(membership("t1", "A", 100L, deletedAt = 100L)),
        )
        val remote = document(
            folders = listOf(folder("A", 10L)),
            tracks = listOf(trackRecord("t1", 10L)),
            memberships = listOf(membership("t1", "A", 100L)),
        )

        val result = FavoriteMerge.merge(local, remote, now = 1_000L, deviceId = "device")

        assertEquals(1, result.activeMemberships)
        assertNull(result.document.memberships.single().deletedAt)
    }

    @Test
    fun membershipWithoutTrackIsDropped() {
        val local = document(
            folders = listOf(folder("A", 10L)),
            memberships = listOf(membership("missing", "A", 10L)),
        )

        val result = FavoriteMerge.merge(local, document(), now = 1_000L, deviceId = "device")

        assertTrue(result.document.memberships.isEmpty())
    }

    @Test
    fun oldTombstonesArePruned() {
        val now = FavoriteMerge.TOMBSTONE_TTL_MS + 10_000L
        val local = document(
            folders = listOf(folder("A", 1L, deletedAt = 1L)),
            tracks = listOf(trackRecord("t1", 1L)),
            memberships = listOf(membership("t1", "A", 1L, deletedAt = 1L)),
        )

        val result = FavoriteMerge.merge(local, document(), now = now, deviceId = "device")

        assertTrue(result.document.folders.isEmpty())
        assertTrue(result.document.memberships.isEmpty())
    }

    @Test
    fun outputIsSortedForStableUploads() {
        val local = document(
            folders = listOf(folder("Z", 10L), folder("A", 10L)),
            tracks = listOf(trackRecord("t2", 10L), trackRecord("t1", 10L)),
            memberships = listOf(
                membership("t2", "Z", 10L),
                membership("t1", "A", 10L),
            ),
        )

        val result = FavoriteMerge.merge(local, document(), now = 1_000L, deviceId = "device")

        assertEquals(listOf("A", "Z"), result.document.folders.map { it.name })
        assertEquals(listOf("t1", "t2"), result.document.tracks.map { it.trackId })
        assertEquals(
            listOf("t1" to "A", "t2" to "Z"),
            result.document.memberships.map { it.trackId to it.folder },
        )
    }
}
