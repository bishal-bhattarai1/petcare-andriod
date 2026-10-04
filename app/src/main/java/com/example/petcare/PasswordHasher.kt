package com.example.petcare

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * Hashes and verifies account passwords with PBKDF2-HMAC-SHA256.
 *
 * Stored format (self-describing, so old hashes stay readable if the settings change):
 *     pbkdf2$<iterations>$<saltBase64>$<hashBase64>
 *
 * Accounts created before this class existed hold a legacy value: hex SHA-256 of
 * "<normalised email>:<password>". [verify] still accepts those and reports
 * [Result.ValidNeedsUpgrade] so the caller can replace them with a PBKDF2 hash.
 *
 * This class has no Android dependencies so it can be unit-tested on the JVM.
 * PBKDF2 is deliberately slow: always call [hash] and [verify] off the main thread.
 *
 * @param iterations PBKDF2 work factor for new hashes. Tests pass a small value to stay fast.
 */
class PasswordHasher(private val iterations: Int = DEFAULT_ITERATIONS) {

    /** Outcome of checking a password against a stored value. */
    enum class Result {
        /** Password is correct and the stored hash is up to date. */
        Valid,

        /** Password is correct but the stored hash is legacy or weaker than current settings: re-hash and save it. */
        ValidNeedsUpgrade,

        /** Wrong password, or the stored value is not a password hash (e.g. a Google-only account). */
        Invalid
    }

    private val random = SecureRandom()

    /** Creates a new PBKDF2 hash with a fresh random salt for [password]. */
    fun hash(password: String): String {
        val salt = ByteArray(SALT_BYTES).also { random.nextBytes(it) }
        val derived = pbkdf2(password, salt, iterations)
        val encoder = Base64.getEncoder()
        return listOf(PREFIX, iterations, encoder.encodeToString(salt), encoder.encodeToString(derived))
            .joinToString(SEPARATOR)
    }

    /**
     * Checks [password] against the [stored] value from the database.
     * [email] is only needed for legacy SHA-256 hashes, which were salted with the email.
     */
    fun verify(password: String, stored: String, email: String): Result {
        if (stored.startsWith(PREFIX + SEPARATOR)) return verifyPbkdf2(password, stored)
        if (isLegacyHash(stored)) {
            val candidate = legacyHash(password, email).toByteArray()
            // Constant-time comparison so response time doesn't reveal how many characters matched.
            return if (MessageDigest.isEqual(candidate, stored.lowercase().toByteArray())) {
                Result.ValidNeedsUpgrade
            } else {
                Result.Invalid
            }
        }
        // Placeholders such as "SOCIAL_LOGIN_NO_PASSWORD" / "GUEST_HASH" never match any password.
        return Result.Invalid
    }

    /**
     * Burns the same amount of CPU as a real check. Used when the email doesn't exist so an
     * attacker can't tell "unknown email" from "wrong password" by timing the login.
     */
    fun dummyVerify(password: String) {
        pbkdf2(password, ByteArray(SALT_BYTES), iterations)
    }

    private fun verifyPbkdf2(password: String, stored: String): Result {
        // Expected parts: ["pbkdf2", iterations, salt, hash]
        val parts = stored.split(SEPARATOR)
        if (parts.size != 4) return Result.Invalid
        val storedIterations = parts[1].toIntOrNull()?.takeIf { it > 0 } ?: return Result.Invalid
        val decoder = Base64.getDecoder()
        val salt: ByteArray
        val expected: ByteArray
        try {
            salt = decoder.decode(parts[2])
            expected = decoder.decode(parts[3])
        } catch (_: IllegalArgumentException) {
            return Result.Invalid // corrupted Base64
        }
        if (salt.isEmpty() || expected.isEmpty()) return Result.Invalid

        // Re-derive with the SAME salt and iteration count that were stored, then compare in constant time.
        val actual = pbkdf2(password, salt, storedIterations, expected.size * 8)
        if (!MessageDigest.isEqual(actual, expected)) return Result.Invalid

        // Correct password: ask for an upgrade if this hash was made with a lower work factor.
        return if (storedIterations < iterations) Result.ValidNeedsUpgrade else Result.Valid
    }

    private fun pbkdf2(password: String, salt: ByteArray, rounds: Int, keyBits: Int = KEY_BITS): ByteArray {
        val spec = PBEKeySpec(password.toCharArray(), salt, rounds, keyBits)
        try {
            return SecretKeyFactory.getInstance(ALGORITHM).generateSecret(spec).encoded
        } finally {
            spec.clearPassword() // don't leave the password chars lying around in memory
        }
    }

    companion object {
        /**
         * Work factor for new hashes. OWASP recommends 600,000 for PBKDF2-HMAC-SHA256, but Android's
         * PBKDF2 is pure Java (Bouncy Castle): 600,000 took 8-13 s per login on the test emulator.
         * 100,000 measured ~0.9 s, inside the ~1 s login budget. The count is stored in each hash, so
         * raising it later automatically upgrades users on their next login.
         */
        const val DEFAULT_ITERATIONS = 100_000

        /** Minimum length for any new password (sign-up, reset, change). */
        const val MIN_PASSWORD_LENGTH = 8

        private const val ALGORITHM = "PBKDF2WithHmacSHA256"
        private const val PREFIX = "pbkdf2"
        private const val SEPARATOR = "$"
        private const val SALT_BYTES = 16
        private const val KEY_BITS = 256

        private val LEGACY_HEX = Regex("^[0-9a-fA-F]{64}$")

        /** True for a pre-PBKDF2 value (64 hex characters of SHA-256). */
        fun isLegacyHash(stored: String): Boolean = LEGACY_HEX.matches(stored)

        /** The old scheme, kept only so existing accounts can still log in once and be upgraded. */
        fun legacyHash(password: String, email: String): String {
            val salted = "${email.trim().lowercase()}:$password"
            val bytes = MessageDigest.getInstance("SHA-256").digest(salted.toByteArray())
            return bytes.joinToString("") { "%02x".format(it) }
        }
    }
}
