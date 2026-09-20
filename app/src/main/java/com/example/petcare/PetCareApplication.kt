package com.example.petcare

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate

class PetCareApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        val sessionManager = SessionManager(this)
        AppCompatDelegate.setDefaultNightMode(sessionManager.getThemeMode())
        
        NotificationHelper(this).createNotificationChannel()
    }
}
