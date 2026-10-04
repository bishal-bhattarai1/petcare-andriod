package com.example.petcare

import androidx.annotation.WorkerThread
import com.example.petcare.data.HealthRepository
import com.example.petcare.data.PetRepository
import com.example.petcare.data.TaskRepository
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

    /** True if the routine's alarm intent still exists (it is cleared by reboot and force-stop). */
    fun isScheduled(context: Context, taskId: Long): Boolean = pendingIntent(context, taskId, create = false) != null

    /** True if this routine should currently have an alarm: reminders on and a next time exists. */
    fun needsAlarm(task: CareTask): Boolean = task.reminderEnabled && TaskSchedule.nextOccurrence(task) != null

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
        val at = dueAt(date) ?: return
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

    /** 9:00 AM on the record's [date] if that is still in the future, else null (no reminder is needed). */
    fun dueAt(date: String): Calendar? {
        val parsed = parseExpenseDate(date) ?: return null
        val at = Calendar.getInstance().apply {
            time = parsed
            set(Calendar.HOUR_OF_DAY, 9); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        return at.takeIf { it.after(Calendar.getInstance()) }
    }

    fun cancel(context: Context, recordId: Long) = cancelAlarm(context, existing(context, recordId))

    /** True if the record's alarm intent still exists (it is cleared by reboot and force-stop). */
    fun isScheduled(context: Context, recordId: Long): Boolean = existing(context, recordId) != null

    private fun existing(context: Context, recordId: Long): PendingIntent? = PendingIntent.getBroadcast(
        context, requestCode(recordId), Intent(context, ReminderReceiver::class.java),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_NO_CREATE
    )

    // Offset keeps record alarms apart from task (taskId) and vaccine (petId + 10000) alarms.
    private fun requestCode(recordId: Long) = 500_000 + recordId.toInt()
}

/** Keeps a pet's health schedule in sync when a record is added from any screen or import. */
object HealthSchedule {

    /**
     * Call after saving a health record. A future vaccination becomes the pet's "Next vaccination"
     * (if it's sooner than the current one); other future records get a reminder on the day.
     * Reads and writes the database: call from a background thread.
     */
    @WorkerThread
    fun onRecordAdded(context: Context, petId: Long, recordId: Long, type: String, date: String) {
        val due = parseExpenseDate(date) ?: return
        val today = startOfToday()
        if (due.time < today) return // Past records are history; nothing to schedule.

        val pets = PetRepository(context)
        val pet = pets.getPet(petId) ?: return
        val petName = pet.name.ifBlank { "Your pet" }

        if (type.equals("Vaccination", ignoreCase = true)) {
            val current = parseExpenseDate(pet.vaccineDate.orEmpty())
            val isSooner = current == null || current.time < today || due.before(current)
            if (isSooner && pets.updatePetVaccineDate(petId, date)) {
                if (pet.reminderEnabled == 1) VaccineReminder.schedule(context, petId, petName, date)
                return // The vaccination reminder covers this record.
            }
        }
        RecordReminder.schedule(context, recordId, petName, type, date)
    }

    private fun startOfToday(): Long = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis
}

/**
 * Makes sure every reminder that should exist has an exact alarm. Used by the WorkManager workers
 * (daily check and after boot / app update / time change), never on the main thread.
 */
object ReminderScheduler {

    /** How many reminders should exist, and how many of those had no alarm before this run. */
    data class Report(val expected: Int, val missing: Int)

    /**
     * Counts reminders whose alarm is missing, then re-arms ALL reminders that should exist.
     * Re-arming is idempotent (the same PendingIntent just replaces the alarm at the same time),
     * and it also repairs alarms that some battery savers drop without clearing the PendingIntent,
     * which the missing-check alone cannot detect. Blocking: call from a worker.
     */
    @WorkerThread
    fun ensureAll(context: Context): Report {
        var expected = 0
        var missing = 0

        TaskRepository(context).getCareTasks().filter(TaskReminder::needsAlarm).forEach { task ->
            expected++
            if (!TaskReminder.isScheduled(context, task.id)) missing++
            TaskReminder.schedule(context, task)
        }

        val pets = PetRepository(context)
        pets.getPetOptions().forEach { option ->
            val pet = pets.getPet(option.id) ?: return@forEach
            val date = pet.vaccineDate.orEmpty()
            if (pet.reminderEnabled == 1 && VaccineReminder.dueAt(date) != null) {
                expected++
                if (!VaccineReminder.isScheduled(context, pet.id)) missing++
                VaccineReminder.schedule(context, pet.id, option.name, date)
            }
        }

        HealthRepository(context).getAllHealthcareHistory()
            .filter { RecordReminder.dueAt(it.date) != null }
            .forEach { record ->
                expected++
                if (!RecordReminder.isScheduled(context, record.id)) missing++
                RecordReminder.schedule(context, record.id, record.petName, record.type, record.date)
            }

        return Report(expected, missing)
    }
}
