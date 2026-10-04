package com.example.petcare

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [PasswordHasher]. A low iteration count keeps the suite fast; the
 * algorithm is identical to production, only the work factor differs.
 */
class PasswordHasherTest {

    private val hasher = PasswordHasher(iterations = 1_000)
    private val email = "owner@petcare.app"

    @Test
    fun correctPassword_isValid() {
        val stored = hasher.hash("Correct-Horse-9")
        assertEquals(PasswordHasher.Result.Valid, hasher.verify("Correct-Horse-9", stored, email))
    }

    @Test
    fun wrongPassword_isInvalid() {
        val stored = hasher.hash("Correct-Horse-9")
        assertEquals(PasswordHasher.Result.Invalid, hasher.verify("correct-horse-9", stored, email))
        assertEquals(PasswordHasher.Result.Invalid, hasher.verify("", stored, email))
    }

    @Test
    fun samePassword_getsDifferentSaltAndHash() {
        val first = hasher.hash("SamePassword1")
        val second = hasher.hash("SamePassword1")
        val (_, _, salt1, hash1) = first.split("$")
        val (_, _, salt2, hash2) = second.split("$")

        assertNotEquals(salt1, salt2)
        assertNotEquals(hash1, hash2)
        // ...yet both still verify, because each carries its own salt.
        assertEquals(PasswordHasher.Result.Valid, hasher.verify("SamePassword1", first, email))
        assertEquals(PasswordHasher.Result.Valid, hasher.verify("SamePassword1", second, email))
    }

    @Test
    fun storedFormat_isSelfDescribing() {
        val parts = PasswordHasher(iterations = 1234).hash("Format-Check1").split("$")
        assertEquals(4, parts.size)
        assertEquals("pbkdf2", parts[0])
        assertEquals("1234", parts[1])
        assertEquals(16, java.util.Base64.getDecoder().decode(parts[2]).size) // 16-byte salt
        assertEquals(32, java.util.Base64.getDecoder().decode(parts[3]).size) // 256-bit hash
    }

    @Test
    fun legacySha256_correctPassword_needsUpgrade() {
        val legacy = PasswordHasher.legacyHash("oldpass", email)
        assertTrue(PasswordHasher.isLegacyHash(legacy))
        assertEquals(PasswordHasher.Result.ValidNeedsUpgrade, hasher.verify("oldpass", legacy, email))
    }

    @Test
    fun legacySha256_wrongPassword_isInvalid() {
        val legacy = PasswordHasher.legacyHash("oldpass", email)
        assertEquals(PasswordHasher.Result.Invalid, hasher.verify("oldpass2", legacy, email))
    }

    @Test
    fun legacySha256_emailIsNormalised() {
        // The old scheme salted with trim().lowercase() email, so login with different casing must still match.
        val legacy = PasswordHasher.legacyHash("oldpass", email)
        assertEquals(PasswordHasher.Result.ValidNeedsUpgrade, hasher.verify("oldpass", legacy, "  Owner@PetCare.app "))
    }

    @Test
    fun legacyUpgrade_producesPbkdf2HashThatVerifies() {
        // Simulates AuthDatabaseHelper.checkPassword(): legacy match -> re-hash -> next login is Valid.
        val legacy = PasswordHasher.legacyHash("oldpass", email)
        assertEquals(PasswordHasher.Result.ValidNeedsUpgrade, hasher.verify("oldpass", legacy, email))

        val upgraded = hasher.hash("oldpass")
        assertTrue(upgraded.startsWith("pbkdf2$"))
        assertEquals(PasswordHasher.Result.Valid, hasher.verify("oldpass", upgraded, email))
    }

    @Test
    fun lowerIterationCount_needsUpgrade() {
        val weak = PasswordHasher(iterations = 500).hash("Rehash-Me-1")
        assertEquals(PasswordHasher.Result.ValidNeedsUpgrade, hasher.verify("Rehash-Me-1", weak, email))
    }

    @Test
    fun placeholdersAndMalformedValues_neverMatch() {
        listOf(
            "SOCIAL_LOGIN_NO_PASSWORD",
            "GUEST_HASH",
            "",
            "pbkdf2\$",
            "pbkdf2\$abc\$c2FsdA==\$aGFzaA==",
            "pbkdf2\$1000\$!!notbase64!!\$aGFzaA==",
            "pbkdf2\$1000\$\$"
        ).forEach { stored ->
            assertEquals("stored=$stored", PasswordHasher.Result.Invalid, hasher.verify("anything1", stored, email))
        }
    }
}
