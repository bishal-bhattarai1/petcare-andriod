package com.example.petcare

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/*
 * Hybrid reminder design:
 *  - AlarmManager exact alarms deliver the reminder itself at the scheduled minute (see Reminders.kt).
 *    WorkManager can't do that: it may defer work by minutes or hours to save battery.
 *  - WorkManager runs the deferrable maintenance that must happen *eventually* and must survive
 *    process death and reboots: re-creating alarms after boot/update and a daily safety check.
 *    It persists its queue, retries with back-off on failure, and isn't limited to the ~10 s
 *    a BroadcastReceiver is allowed to run.
 */

/**
 * Runs about once a day: finds reminders whose alarm went missing (force-stop, aggressive battery
 * savers, a failed earlier reschedule) and re-arms every reminder that should exist.
 */
class ReminderCheckWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            val report = ReminderScheduler.ensureAll(applicationContext)
            Log.i(TAG, "Daily check: ${report.expected} reminders expected, ${report.missing} were missing and re-created")
            Result.success(workDataOf(KEY_EXPECTED to report.expected, KEY_MISSING to report.missing))
        } catch (e: Exception) {
            Log.w(TAG, "Daily reminder check failed, will retry", e)
            Result.retry() // WorkManager retries with exponential back-off
        }
    }
}

/**
 * One-time job that re-creates every alarm after the phone restarts, the app is updated, the clock
 * or time zone changes, or exact-alarm permission is granted. Replaces the work that used to be
 * done inside [ReminderReceiver].
 */
class RescheduleRemindersWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val reason = inputData.getString(KEY_REASON) ?: "unknown"
        try {
            val report = ReminderScheduler.ensureAll(applicationContext)
            Log.i(TAG, "Rescheduled after $reason: ${report.expected} reminders (${report.missing} had no alarm)")
            Result.success(workDataOf(KEY_EXPECTED to report.expected, KEY_MISSING to report.missing))
        } catch (e: Exception) {
            Log.w(TAG, "Rescheduling after $reason failed, will retry", e)
            Result.retry()
        }
    }
}

/** Enqueues the reminder workers. Unique names make sure each job exists only once. */
object ReminderWork {
    const val PERIODIC_CHECK = "reminder-daily-check"
    const val RESCHEDULE = "reminder-reschedule"

    /** Called on every app start; KEEP leaves an already-scheduled daily check untouched. */
    fun schedulePeriodicCheck(context: Context) {
        val request = PeriodicWorkRequestBuilder<ReminderCheckWorker>(1, TimeUnit.DAYS)
            // Not urgent: the alarms themselves are already set, this is only a safety net.
            .setConstraints(Constraints.Builder().setRequiresBatteryNotLow(true).build())
            .build()
        WorkManager.getInstance(context)
            .enqueueUniquePeriodicWork(PERIODIC_CHECK, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    /** Asks for all alarms to be re-created soon. REPLACE collapses several triggers into one run. */
    fun requestReschedule(context: Context, reason: String) {
        val request = OneTimeWorkRequestBuilder<RescheduleRemindersWorker>()
            .setInputData(workDataOf(KEY_REASON to reason))
            .build()
        WorkManager.getInstance(context)
            .enqueueUniqueWork(RESCHEDULE, ExistingWorkPolicy.REPLACE, request)
    }
}

private const val TAG = "PetCareReminders"
internal const val KEY_REASON = "reason"
internal const val KEY_EXPECTED = "expected"
internal const val KEY_MISSING = "missing"
