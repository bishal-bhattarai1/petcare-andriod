package com.example.petcare

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import org.osmdroid.config.Configuration

class PetCareApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        val sessionManager = SessionManager(this)
        AppCompatDelegate.setDefaultNightMode(sessionManager.getThemeMode())
        
        NotificationHelper(this).createNotificationChannel()

        // Configure Osmdroid User-Agent to satisfy map tile policy and prevent 403 Access Blocked errors
        Configuration.getInstance().userAgentValue = "Mozilla/5.0 (Linux; Android 13; Mobile) PetCareApp/1.0"
        Configuration.getInstance().load(this, getSharedPreferences("osmdroid", MODE_PRIVATE))
    }
}
