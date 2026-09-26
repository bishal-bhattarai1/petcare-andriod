package com.example.petcare

import androidx.appcompat.app.AppCompatDelegate

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
        private const val KEY_REMEMBERED_EMAIL = "remembered_email"
        private const val KEY_BIOMETRIC_EMAIL = "biometric_email"
    }

    /** Account that biometric unlock signs into: the last one that signed in on this device. */
    fun setBiometricEmail(email: String) {
        prefs.edit().putString(KEY_BIOMETRIC_EMAIL, email).apply()
    }

    fun getBiometricEmail(): String? = prefs.getString(KEY_BIOMETRIC_EMAIL, null)

    fun saveEmail(email: String) {
        prefs.edit().putString(KEY_REMEMBERED_EMAIL, email).apply()
    }

    fun getSavedEmail(): String? = prefs.getString(KEY_REMEMBERED_EMAIL, null)

    fun clearSavedEmail() {
        prefs.edit().remove(KEY_REMEMBERED_EMAIL).apply()
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

    /** Light or dark only; defaults to light (also for the old "System" setting). */
    fun getThemeMode(): Int =
        if (prefs.getInt(KEY_THEME_MODE, AppCompatDelegate.MODE_NIGHT_NO) == AppCompatDelegate.MODE_NIGHT_YES) {
            AppCompatDelegate.MODE_NIGHT_YES
        } else {
            AppCompatDelegate.MODE_NIGHT_NO
        }

    fun setThemeMode(mode: Int) {
        prefs.edit().putInt(KEY_THEME_MODE, mode).apply()
    }

    fun getProfileImageUri(): String? = prefs.getString(KEY_PROFILE_IMAGE_URI, null)

    fun setProfileImageUri(uri: String?) {
        prefs.edit().putString(KEY_PROFILE_IMAGE_URI, uri).apply()
    }

    fun logout() {
        // Keep the "Remember me" email and the biometric account so they survive logging out.
        val rememberedEmail = getSavedEmail()
        val biometricEmail = getBiometricEmail()
        prefs.edit().apply {
            clear()
            rememberedEmail?.let { putString(KEY_REMEMBERED_EMAIL, it) }
            biometricEmail?.let { putString(KEY_BIOMETRIC_EMAIL, it) }
            apply()
        }
    }
}
