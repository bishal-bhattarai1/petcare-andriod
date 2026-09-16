package com.example.petcare.utils

import java.security.MessageDigest

object PasswordUtils {
    fun hashPassword(password: String): String {
        return try {
            val digest = MessageDigest.getInstance("SHA-256")
            val hashBytes = digest.digest(password.toByteArray(Charsets.UTF_8))
            val hexString = StringBuilder()
            for (b in hashBytes) {
                val hex = Integer.toHexString(0xff and b.toInt())
                if (hex.length == 1) hexString.append('0')
                hexString.append(hex)
            }
            hexString.toString()
        } catch (e: Exception) {
            password // Fallback to raw password if algorithm is missing (unlikely)
        }
    }
}