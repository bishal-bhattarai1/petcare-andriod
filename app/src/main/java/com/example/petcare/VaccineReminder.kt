package com.example.petcare

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import java.util.Calendar

/** Schedules the 9:00 AM "vaccination due" notification for a pet. One alarm per pet. */
object VaccineReminder {

    /** Schedules (or replaces) the reminder for [date] ("dd/MM/yyyy"). Past dates are ignored. */
    fun schedule(context: Context, petId: Long, petName: String, date: String) {
        val parsed = parseExpenseDate(date) ?: return
        val calendar = Calendar.getInstance().apply {
            time = parsed
            set(Calendar.HOUR_OF_DAY, 9)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
        }
        if (calendar.before(Calendar.getInstance())) return

        val pending = pendingIntent(context, petId, petName) ?: return
        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
                alarmManager.set(AlarmManager.RTC_WAKEUP, calendar.timeInMillis, pending)
            } else {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, calendar.timeInMillis, pending)
            }
        } catch (_: SecurityException) {
            alarmManager.set(AlarmManager.RTC_WAKEUP, calendar.timeInMillis, pending)
        }
    }

    fun cancel(context: Context, petId: Long) {
        val pending = pendingIntent(context, petId, null) ?: return
        context.getSystemService(AlarmManager::class.java)?.cancel(pending)
        pending.cancel()
    }

    /** With a name it creates/updates the alarm intent; without one it only finds an existing one. */
    private fun pendingIntent(context: Context, petId: Long, petName: String?): PendingIntent? {
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            putExtra("PET_ID", petId)
            petName?.let { putExtra("PET_NAME", it) }
            putExtra("TYPE", "VACCINE")
        }
        // petId + 10000 keeps vaccine alarms apart from task alarms.
        val flags = PendingIntent.FLAG_IMMUTABLE or
            if (petName != null) PendingIntent.FLAG_UPDATE_CURRENT else PendingIntent.FLAG_NO_CREATE
        return PendingIntent.getBroadcast(context, petId.toInt() + 10000, intent, flags)
    }
}
