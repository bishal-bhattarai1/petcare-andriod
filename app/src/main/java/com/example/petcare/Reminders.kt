package com.example.petcare

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import java.util.Calendar

/** Exact alarm helper shared by all reminders (falls back to inexact if exact alarms aren't allowed). */
internal fun scheduleAlarm(context: Context, atMillis: Long, pending: PendingIntent) {
    val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
    try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, pending)
        } else {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, pending)
        }
    } catch (_: SecurityException) {
        alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, pending)
    }
}

internal fun cancelAlarm(context: Context, pending: PendingIntent?) {
    pending ?: return
    context.getSystemService(AlarmManager::class.java)?.cancel(pending)
    pending.cancel()
}

/**
 * Routine reminders. One alarm per task for its *next* occurrence; when it fires,
 * [ReminderReceiver] shows the notification and schedules the one after, so daily,
 * weekly and monthly reminders keep repeating.
 */
object TaskReminder {

    fun schedule(context: Context, task: CareTask) {
        cancel(context, task.id)
        if (!task.reminderEnabled) return
        val next = TaskSchedule.nextOccurrence(task) ?: return
        scheduleAlarm(context, next.timeInMillis, pendingIntent(context, task.id, create = true)!!)
    }

    fun cancel(context: Context, taskId: Long) = cancelAlarm(context, pendingIntent(context, taskId, create = false))

    private fun pendingIntent(context: Context, taskId: Long, create: Boolean): PendingIntent? {
        val intent = Intent(context, ReminderReceiver::class.java)
            .putExtra(ReminderReceiver.EXTRA_TYPE, ReminderReceiver.TYPE_TASK)
            .putExtra(ReminderReceiver.EXTRA_TASK_ID, taskId)
        val flags = PendingIntent.FLAG_IMMUTABLE or if (create) PendingIntent.FLAG_UPDATE_CURRENT else PendingIntent.FLAG_NO_CREATE
        return PendingIntent.getBroadcast(context, taskId.toInt(), intent, flags)
    }
}

/** 9:00 AM reminder on the day of an upcoming health record (vet visit, check-up, surgery…). */
object RecordReminder {

    fun schedule(context: Context, recordId: Long, petName: String, type: String, date: String) {
        val parsed = parseExpenseDate(date) ?: return
        val at = Calendar.getInstance().apply {
            time = parsed
            set(Calendar.HOUR_OF_DAY, 9); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        if (!at.after(Calendar.getInstance())) return
        val intent = Intent(context, ReminderReceiver::class.java)
            .putExtra(ReminderReceiver.EXTRA_TYPE, ReminderReceiver.TYPE_RECORD)
            .putExtra(ReminderReceiver.EXTRA_RECORD_ID, recordId)
            .putExtra(ReminderReceiver.EXTRA_PET_NAME, petName)
            .putExtra(ReminderReceiver.EXTRA_RECORD_TYPE, type)
        val pending = PendingIntent.getBroadcast(
            context, requestCode(recordId), intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        scheduleAlarm(context, at.timeInMillis, pending)
    }

    fun cancel(context: Context, recordId: Long) {
        val intent = Intent(context, ReminderReceiver::class.java)
        cancelAlarm(
            context,
            PendingIntent.getBroadcast(context, requestCode(recordId), intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_NO_CREATE)
        )
    }

    // Offset keeps record alarms apart from task (taskId) and vaccine (petId + 10000) alarms.
    private fun requestCode(recordId: Long) = 500_000 + recordId.toInt()
}

/** Keeps a pet's health schedule in sync when a record is added from any screen or import. */
object HealthSchedule {

    /**
     * Call after saving a health record. A future vaccination becomes the pet's "Next vaccination"
     * (if it's sooner than the current one); other future records get a reminder on the day.
     */
    fun onRecordAdded(context: Context, database: AuthDatabaseHelper, petId: Long, recordId: Long, type: String, date: String) {
        val due = parseExpenseDate(date) ?: return
        val today = startOfToday()
        if (due.time < today) return // Past records are history; nothing to schedule.

        val pet = database.getPetById(petId) ?: return
        val petName = pet.getAsString("name").orEmpty().ifBlank { "Your pet" }

        if (type.equals("Vaccination", ignoreCase = true)) {
            val current = parseExpenseDate(pet.getAsString("vaccine_date").orEmpty())
            val isSooner = current == null || current.time < today || due.before(current)
            if (isSooner && database.updatePetVaccineDate(petId, date)) {
                if (pet.getAsInteger("reminder_enabled") == 1) VaccineReminder.schedule(context, petId, petName, date)
                return // The vaccination reminder covers this record.
            }
        }
        RecordReminder.schedule(context, recordId, petName, type, date)
    }

    private fun startOfToday(): Long = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis
}
