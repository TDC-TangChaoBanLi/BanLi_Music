/**
 * 文件作用：使用 Android Keystore 加密保存 OneDrive 令牌与待完成的 OAuth 会话。
 */
package com.banlimusic.app.core.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import android.util.Base64
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

@Serializable
data class StoredAuth(
    val refreshToken: String? = null,
    val accessToken: String? = null,
    val expiresAt: Long = 0L,
    val scope: String? = null,
    val pendingState: String? = null,
    val pendingVerifier: String? = null,
    val pendingCreatedAt: Long = 0L,
) {
    val hasSession: Boolean get() = !refreshToken.isNullOrBlank()
}

/**
 * Tokens live in `noBackupFilesDir` so Android auto backup never uploads them;
 * the file itself is AES-GCM encrypted with a non-exportable Keystore key.
 */
class AndroidTokenStore(
    context: Context,
    private val json: Json,
) {
    private val file = File(context.noBackupFilesDir, "onedrive_auth.bin")
    private val atomicFile = AtomicFile(file)

    @Synchronized
    fun load(): StoredAuth? {
        if (!file.exists()) return null
        return runCatching {
            val encrypted = atomicFile.readFully()
            if (encrypted.isEmpty()) return null
            val text = String(encrypted, Charsets.UTF_8)
            val separator = text.indexOf(':')
            if (separator <= 0) return null
            val iv = Base64.decode(text.substring(0, separator), Base64.NO_WRAP)
            val payload = Base64.decode(text.substring(separator + 1), Base64.NO_WRAP)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, secretKey(), javax.crypto.spec.GCMParameterSpec(128, iv))
            val plain = cipher.doFinal(payload)
            json.decodeFromString(StoredAuth.serializer(), String(plain, Charsets.UTF_8))
        }.getOrElse {
            // A wiped or invalidated Keystore key must force a fresh sign-in.
            runCatching { atomicFile.delete() }
            null
        }
    }

    @Synchronized
    fun save(auth: StoredAuth) {
        val payload = json.encodeToString(StoredAuth.serializer(), auth).toByteArray(Charsets.UTF_8)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val encrypted = cipher.doFinal(payload)
        val text = Base64.encodeToString(cipher.iv, Base64.NO_WRAP) +
            ":" +
            Base64.encodeToString(encrypted, Base64.NO_WRAP)
        val output = atomicFile.startWrite()
        try {
            output.write(text.toByteArray(Charsets.UTF_8))
            atomicFile.finishWrite(output)
        } catch (e: Exception) {
            atomicFile.failWrite(output)
            throw e
        }
    }

    @Synchronized
    fun clear() {
        runCatching { atomicFile.delete() }
    }

    private fun secretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        val existing = (keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.secretKey
        if (existing != null) return existing
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return generator.generateKey()
    }

    private companion object {
        const val KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "banlimusic_onedrive"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
    }
}

private fun AtomicFile.readFully(): ByteArray = openRead().use { it.readBytes() }
