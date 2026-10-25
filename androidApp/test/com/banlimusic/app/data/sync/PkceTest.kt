package com.banlimusic.app.data.sync

import okio.ByteString.Companion.toByteString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.MessageDigest

class PkceTest {

    @Test
    fun verifierIsUrlSafeAndLongEnough() {
        val verifier = Pkce.codeVerifier()
        assertEquals(64, verifier.length)
        assertTrue(verifier.all { it.isLetterOrDigit() || it == '-' || it == '_' })
    }

    @Test
    fun challengeIsUnpaddedSha256Base64Url() {
        val verifier = Pkce.codeVerifier()
        val expected = MessageDigest.getInstance("SHA-256")
            .digest(verifier.toByteArray(Charsets.US_ASCII))
            .toByteString()
            .base64Url(false)
        val challenge = Pkce.codeChallenge(verifier)
        assertEquals(expected, challenge)
        // Entra rejects any other size with AADSTS501491.
        assertEquals(43, challenge.length)
        assertTrue(challenge.none { it == '=' })
    }

    @Test
    fun stateIsRandomAndUnpadded() {
        val first = Pkce.state()
        val second = Pkce.state()
        assertNotEquals(first, second)
        assertTrue(first.none { it == '=' })
    }
}
