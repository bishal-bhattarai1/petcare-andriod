package com.example.petcare

import android.content.Context
import android.content.SharedPreferences

class SessionManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("PetCarePrefs", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_USER_NAME = "user_name"
        private const val KEY_USER_EMAIL = "user_email"
        private const val KEY_IS_LOGGED_IN = "is_logged_in"
        private const val KEY_NOTIFICATIONS_ENABLED = "notifications_enabled"
        private const val KEY_DEFAULT_DELEGATE_CONTACT = "default_delegate_contact"
        private const val KEY_THEME_MODE = "theme_mode"
        private const val KEY_PROFILE_IMAGE_URI = "profile_image_uri"
    }

    fun saveUser(name: String, email: String) {
        prefs.edit().apply {
            putString(KEY_USER_NAME, name)
            putString(KEY_USER_EMAIL, email)
            putBoolean(KEY_IS_LOGGED_IN, true)
            apply()
        }
    }

    fun updateUserInfo(name: String, email: String) {
        prefs.edit().apply {
            putString(KEY_USER_NAME, name)
            putString(KEY_USER_EMAIL, email)
            apply()
        }
    }

    fun getUserName(): String? = prefs.getString(KEY_USER_NAME, "User")

    fun getUserEmail(): String? = prefs.getString(KEY_USER_EMAIL, "")

    fun isLoggedIn(): Boolean = prefs.getBoolean(KEY_IS_LOGGED_IN, false)

    fun areNotificationsEnabled(): Boolean =
        prefs.getBoolean(KEY_NOTIFICATIONS_ENABLED, true)

    fun setNotificationsEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_NOTIFICATIONS_ENABLED, enabled).apply()
    }

    fun getDefaultDelegateContact(): String =
        prefs.getString(KEY_DEFAULT_DELEGATE_CONTACT, "").orEmpty()

    fun setDefaultDelegateContact(contact: String) {
        prefs.edit().putString(KEY_DEFAULT_DELEGATE_CONTACT, contact).apply()
    }

    fun getThemeMode(): Int = prefs.getInt(KEY_THEME_MODE, -1) // Default to System (-1)

    fun setThemeMode(mode: Int) {
        prefs.edit().putInt(KEY_THEME_MODE, mode).apply()
    }

    fun getProfileImageUri(): String? = prefs.getString(KEY_PROFILE_IMAGE_URI, null)

    fun setProfileImageUri(uri: String?) {
        prefs.edit().putString(KEY_PROFILE_IMAGE_URI, uri).apply()
    }

    fun logout() {
        prefs.edit().clear().apply()
    }
}
