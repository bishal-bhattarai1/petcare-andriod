package com.example.petcare

import android.os.Build
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Measures how long one PBKDF2 hash and one verify take on the device/emulator it runs on,
 * so the iteration count can be checked against the ~1 second login budget.
 * Results are printed to logcat under the tag "PasswordTiming".
 */
@RunWith(AndroidJUnit4::class)
class PasswordHasherTimingTest {

    @Test
    fun measureDefaultIterations() {
        val hasher = PasswordHasher() // production settings (600,000 iterations)
        hasher.hash("warm-up-password") // first call loads the crypto provider; don't time it

        val runs = 3
        var hashMs = 0L
        var verifyMs = 0L
        repeat(runs) {
            var start = System.nanoTime()
            val stored = hasher.hash("Timing-Test-123")
            hashMs += (System.nanoTime() - start) / 1_000_000

            start = System.nanoTime()
            val result = hasher.verify("Timing-Test-123", stored, "timing@petcare.app")
            verifyMs += (System.nanoTime() - start) / 1_000_000
            assertEquals(PasswordHasher.Result.Valid, result)
        }

        // Which JCA provider implements PBKDF2 here (affects speed: native vs pure Java).
        val provider = javax.crypto.SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").provider.name
        Log.i(
            "PasswordTiming",
            "API ${Build.VERSION.SDK_INT} ${Build.MODEL} [$provider]: ${PasswordHasher.DEFAULT_ITERATIONS} iterations -> " +
                "hash avg ${hashMs / runs} ms, verify (login) avg ${verifyMs / runs} ms"
        )
    }

    /** Times a single verify at several work factors to help pick a value that fits the login budget. */
    @Test
    fun measureCandidateIterations() {
        listOf(50_000, 100_000, 210_000, 310_000).forEach { rounds ->
            val hasher = PasswordHasher(iterations = rounds)
            val stored = hasher.hash("Timing-Test-123")
            val start = System.nanoTime()
            hasher.verify("Timing-Test-123", stored, "timing@petcare.app")
            val ms = (System.nanoTime() - start) / 1_000_000
            Log.i("PasswordTiming", "API ${Build.VERSION.SDK_INT}: $rounds iterations -> verify $ms ms")
        }
    }
}
