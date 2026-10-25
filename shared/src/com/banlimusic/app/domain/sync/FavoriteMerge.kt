package com.banlimusic.app.domain.sync

import com.banlimusic.app.core.model.FavoriteSyncDocument
import com.banlimusic.app.core.model.SYNC_SCHEMA_VERSION
import com.banlimusic.app.core.model.SyncFolder
import com.banlimusic.app.core.model.SyncMembership
import com.banlimusic.app.core.model.SyncTrack

data class FavoriteMergeResult(
    val document: FavoriteSyncDocument,
    val addedMemberships: Int,
    val removedMemberships: Int,
    val addedFolders: Int,
) {
    /** Memberships that are visible after the merge (tombstones excluded). */
    val activeMemberships: Int
        get() = document.memberships.count { it.deletedAt == null }
}

/**
 * Pure, deterministic three-way-free merge used by both directions of the sync.
 *
 * Rules:
 * - Folder/membership records are last-write-wins by `updatedAt`.
 * - On equal timestamps the non-deleted record wins, so a network race never
 *   silently deletes a favourite.
 * - A folder tombstone hides memberships that are not newer than the deletion;
 *   a newer membership resurrects the folder (the user re-added it).
 * - Tombstones older than [TOMBSTONE_TTL_MS] are pruned to keep the document small.
 */
object FavoriteMerge {

    const val TOMBSTONE_TTL_MS: Long = 180L * 24L * 60L * 60L * 1000L

    fun merge(
        local: FavoriteSyncDocument,
        remote: FavoriteSyncDocument,
        now: Long,
        deviceId: String,
        tombstoneTtlMs: Long = TOMBSTONE_TTL_MS,
    ): FavoriteMergeResult {
        val localFolders = local.folders.toFolderMap()
        val remoteFolders = remote.folders.toFolderMap()
        val folders = mergeByKey(localFolders, remoteFolders, ::pickFolder).toMutableMap()

        val localTracks = local.tracks.mapNotNull { it.normalized() }.associateBy { it.trackId }
        val remoteTracks = remote.tracks.mapNotNull { it.normalized() }.associateBy { it.trackId }
        val tracks = mergeByKey(localTracks, remoteTracks) { a, b ->
            if (a.updatedAt != b.updatedAt) {
                if (a.updatedAt > b.updatedAt) a else b
            } else {
                a
            }
        }.toMutableMap()

        val localMemberships = local.memberships.toMembershipMap()
        val remoteMemberships = remote.memberships.toMembershipMap()
        val memberships =
            mergeByKey(localMemberships, remoteMemberships, ::pickMembership).toMutableMap()

        // Every membership needs a folder record; older clients may omit empty folders.
        memberships.values.forEach { membership ->
            val folder = folders[membership.folder]
            if (folder == null) {
                folders[membership.folder] = SyncFolder(
                    name = membership.folder,
                    createdAt = membership.addedAt,
                    updatedAt = membership.updatedAt,
                )
            }
        }

        // Folders deleted after a membership was created hide that membership.
        val deletedFolders = folders.values.filter { it.deletedAt != null }
        deletedFolders.forEach { folder ->
            val deletedAt = folder.deletedAt ?: return@forEach
            memberships.values.removeAll { membership ->
                membership.folder == folder.name && membership.updatedAt <= deletedAt
            }
        }
        // A newer membership means the user re-created the folder on another device.
        folders.values.toList().forEach { folder ->
            val deletedAt = folder.deletedAt ?: return@forEach
            val newest = memberships.values
                .filter { it.folder == folder.name }
                .maxOfOrNull { it.updatedAt }
            if (newest != null && newest > deletedAt) {
                folders[folder.name] = folder.copy(deletedAt = null, updatedAt = newest)
            }
        }

        // A membership without track metadata cannot be rendered; dropping it is
        // safer than showing a broken favourite.
        memberships.values.removeAll { tracks[it.trackId] == null }

        val cutoff = now - tombstoneTtlMs
        folders.values.removeAll { folder ->
            val deletedAt = folder.deletedAt
            deletedAt != null && deletedAt < cutoff
        }
        memberships.values.removeAll { membership ->
            val deletedAt = membership.deletedAt
            deletedAt != null && deletedAt < cutoff
        }

        val mergedDocument = FavoriteSyncDocument(
            schemaVersion = SYNC_SCHEMA_VERSION,
            generatedAt = now,
            deviceId = deviceId,
            folders = folders.values
                .filter { it.name.isNotBlank() }
                .sortedBy { it.name },
            tracks = tracks.values.sortedBy { it.trackId },
            memberships = memberships.values
                .filter { it.folder.isNotBlank() }
                .sortedWith(compareBy({ it.trackId }, { it.folder })),
        )

        val localActiveFolders = localFolders.values.filter { it.deletedAt == null }.map { it.name }.toSet()
        val mergedActiveFolders = mergedDocument.folders.filter { it.deletedAt == null }.map { it.name }.toSet()
        val localActiveMemberships = localMemberships.values
            .filter { it.deletedAt == null }
            .map { it.trackId to it.folder }
            .toSet()
        val mergedActiveMemberships = mergedDocument.memberships
            .filter { it.deletedAt == null }
            .map { it.trackId to it.folder }
            .toSet()

        return FavoriteMergeResult(
            document = mergedDocument,
            addedMemberships = (mergedActiveMemberships - localActiveMemberships).size,
            removedMemberships = (localActiveMemberships - mergedActiveMemberships).size,
            addedFolders = (mergedActiveFolders - localActiveFolders).size,
        )
    }

    private fun SyncTrack.normalized(): SyncTrack? {
        val id = track.id.trim().ifBlank { trackId.trim() }
        if (id.isBlank()) return null
        return copy(trackId = id)
    }

    private fun List<SyncFolder>.toFolderMap(): Map<String, SyncFolder> {
        val result = LinkedHashMap<String, SyncFolder>()
        this.forEach { folder ->
            val name = folder.name.trim()
            if (name.isBlank()) return@forEach
            val value = folder.copy(name = name)
            result[name] = result[name]?.let { pickFolder(it, value) } ?: value
        }
        return result
    }

    private fun List<SyncMembership>.toMembershipMap(): Map<String, SyncMembership> {
        val result = LinkedHashMap<String, SyncMembership>()
        this.forEach { membership ->
            val folder = membership.folder.trim()
            val trackId = membership.trackId.trim()
            if (folder.isBlank() || trackId.isBlank()) return@forEach
            val value = membership.copy(trackId = trackId, folder = folder)
            val key = membershipKey(trackId, folder)
            result[key] = result[key]?.let { pickMembership(it, value) } ?: value
        }
        return result
    }

    private fun membershipKey(trackId: String, folder: String): String = "$trackId\u0000$folder"

    private fun pickFolder(a: SyncFolder, b: SyncFolder): SyncFolder {
        if (a.updatedAt != b.updatedAt) return if (a.updatedAt > b.updatedAt) a else b
        val aDeleted = a.deletedAt != null
        val bDeleted = b.deletedAt != null
        if (aDeleted != bDeleted) return if (aDeleted) b else a
        return a
    }

    private fun pickMembership(a: SyncMembership, b: SyncMembership): SyncMembership {
        if (a.updatedAt != b.updatedAt) return if (a.updatedAt > b.updatedAt) a else b
        val aDeleted = a.deletedAt != null
        val bDeleted = b.deletedAt != null
        if (aDeleted != bDeleted) return if (aDeleted) b else a
        return a
    }

    private fun <T> mergeByKey(
        local: Map<String, T>,
        remote: Map<String, T>,
        pick: (T, T) -> T,
    ): Map<String, T> {
        val result = LinkedHashMap<String, T>(local)
        remote.forEach { (key, value) ->
            result[key] = result[key]?.let { pick(it, value) } ?: value
        }
        return result
    }
}
