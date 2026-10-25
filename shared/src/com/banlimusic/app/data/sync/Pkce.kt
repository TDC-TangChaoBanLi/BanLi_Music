package com.banlimusic.app.data.sync

import okio.ByteString.Companion.toByteString
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * OAuth 2.0 PKCE helpers for public clients. Okio is already on the classpath
 * through OkHttp, so no extra dependency is required.
 */
object Pkce {

    private val random = SecureRandom()

    /** 64 characters, within the 43..128 range required by RFC 7636. */
    fun codeVerifier(): String {
        val bytes = ByteArray(48)
        random.nextBytes(bytes)
        // RFC 7636 requires Base64URL *without* padding; Okio pads by default.
        return bytes.toByteString().base64Url(false)
    }

    fun codeChallenge(verifier: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.US_ASCII))
        // S256 challenges must be exactly 43 unpadded characters.
        return digest.toByteString().base64Url(false)
    }

    fun state(): String {
        val bytes = ByteArray(24)
        random.nextBytes(bytes)
        return bytes.toByteString().base64Url(false)
    }
}
