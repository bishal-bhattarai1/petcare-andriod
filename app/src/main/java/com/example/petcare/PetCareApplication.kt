package com.example.petcare

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import org.osmdroid.config.Configuration

class PetCareApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        cleanUpObsoleteDatabases()

        val sessionManager = SessionManager(this)
        AppCompatDelegate.setDefaultNightMode(sessionManager.getThemeMode())
        
        NotificationHelper(this).createNotificationChannel()

        // Daily WorkManager safety check that re-creates any missing reminder alarms (unique, so
        // calling this on every start never creates duplicates).
        ReminderWork.schedulePeriodicCheck(this)

        // Configure Osmdroid User-Agent to satisfy map tile policy and prevent 403 Access Blocked errors
        Configuration.getInstance().userAgentValue = "Mozilla/5.0 (Linux; Android 13; Mobile) PetCareApp/1.0"
        Configuration.getInstance().load(this, getSharedPreferences("osmdroid", MODE_PRIVATE))
    }

    private fun cleanUpObsoleteDatabases() {
        val legacyDatabases = listOf("petcare.db", "petcare_database", "petcare_v2.db")
        for (dbName in legacyDatabases) {
            try {
                deleteDatabase(dbName)
            } catch (_: Exception) {
            }
        }
    }
}
