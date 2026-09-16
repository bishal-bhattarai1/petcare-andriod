package com.example.petcare.data

import android.content.Context
import android.content.SharedPreferences

class SessionManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("petcare_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_IS_LOGGED_IN = "is_logged_in"
        private const val KEY_USER_EMAIL = "user_email"
        private const val KEY_USER_NAME = "user_name"
    }

    fun createSession(name: String, email: String) {
        prefs.edit().apply {
            putBoolean(KEY_IS_LOGGED_IN, true)
            putString(KEY_USER_EMAIL, email)
            putString(KEY_USER_NAME, name)
            apply()
        }
    }

    fun isLoggedIn(): Boolean {
        return prefs.getBoolean(KEY_IS_LOGGED_IN, false)
    }

    fun getUserName(): String {
        return prefs.getString(KEY_USER_NAME, "User") ?: "User"
    }

    fun getUserEmail(): String {
        return prefs.getString(KEY_USER_EMAIL, "") ?: ""
    }

    fun logout() {
        prefs.edit().clear().apply()
    }
}