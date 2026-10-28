package com.banlimusic.desktop.data.sync

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.attribute.PosixFilePermissions

@Serializable
data class DesktopStoredAuth(
    val refreshToken: String? = null,
    val accessToken: String? = null,
    val expiresAt: Long = 0L,
    val scope: String? = null,
) {
    val hasSession: Boolean get() = !refreshToken.isNullOrBlank()
}

/** Stores refresh tokens in the user config directory with owner-only permissions. */
class DesktopTokenStore(
    private val file: Path,
    private val json: Json,
) {

    fun load(): DesktopStoredAuth? {
        if (!Files.exists(file)) return null
        return runCatching {
            json.decodeFromString(DesktopStoredAuth.serializer(), Files.readString(file))
        }.getOrNull()
    }

    fun save(auth: DesktopStoredAuth) {
        Files.createDirectories(file.parent)
        val temp = file.resolveSibling("${file.fileName}.tmp")
        Files.writeString(temp, json.encodeToString(DesktopStoredAuth.serializer(), auth))
        runCatching { Files.setPosixFilePermissions(temp, OWNER_ONLY) }
        runCatching {
            Files.move(
                temp,
                file,
                StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE,
            )
        }.onFailure {
            Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING)
        }
        runCatching { Files.setPosixFilePermissions(file, OWNER_ONLY) }
    }

    fun clear() {
        runCatching { Files.deleteIfExists(file) }
    }

    private companion object {
        val OWNER_ONLY = PosixFilePermissions.fromString("rw-------")
    }
}
