package com.example.petcare

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.annotation.WorkerThread
import com.example.petcare.data.TaskRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Shows reminder notifications:
 *  - routines (feeding, walks, medication, grooming…), then schedules the next occurrence,
 *  - vaccinations due today,
 *  - upcoming health records such as vet visits.
 * System events that wipe or invalidate alarms (reboot, app update, clock/time-zone change, exact-alarm
 * permission granted) are handed to WorkManager ([RescheduleRemindersWorker]) instead of being done here.
 */
class ReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action in RESCHEDULE_ACTIONS) {
            // Only enqueue: WorkManager persists the job, retries it if it fails, and isn't bound by
            // the ~10 s limit of a receiver. Re-creating alarms a few seconds later is fine.
            ReminderWork.requestReschedule(context, intent.action.orEmpty().substringAfterLast('.'))
            return
        }

        // A reminder alarm fired: show it now (it must not be deferred).
        // Database reads can't run on the receiver's main thread: keep the broadcast alive with
        // goAsync() and do the work on a background thread, then call finish().
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                handle(context.applicationContext, intent)
            } finally {
                pending.finish()
            }
        }
    }

    @WorkerThread
    private fun handle(context: Context, intent: Intent) {
        val notificationsOn = SessionManager(context).areNotificationsEnabled()
        // Older task alarms carried only TASK_ID, so treat those as task reminders too.
        val type = intent.getStringExtra(EXTRA_TYPE)
            ?: if (intent.hasExtra(EXTRA_TASK_ID)) TYPE_TASK else return

        when (type) {
            TYPE_TASK -> onTaskReminder(context, intent.getLongExtra(EXTRA_TASK_ID, -1L), notificationsOn)
            TYPE_VACCINE -> if (notificationsOn) {
                val petId = intent.getLongExtra(EXTRA_PET_ID, -1L)
                val petName = intent.getStringExtra(EXTRA_PET_NAME) ?: "Your pet"
                NotificationHelper(context).showTaskNotification(
                    petId + 10000, "Vaccination due today", "$petName's vaccination is due today."
                )
            }
            TYPE_RECORD -> if (notificationsOn) {
                val recordId = intent.getLongExtra(EXTRA_RECORD_ID, -1L)
                val petName = intent.getStringExtra(EXTRA_PET_NAME) ?: "Your pet"
                val recordType = intent.getStringExtra(EXTRA_RECORD_TYPE) ?: "Appointment"
                NotificationHelper(context).showTaskNotification(
                    500_000L + recordId, "$recordType today", "$petName has a $recordType scheduled for today."
                )
            }
        }
    }

    private fun onTaskReminder(context: Context, taskId: Long, notificationsOn: Boolean) {
        if (taskId == -1L) return
        val task = TaskRepository(context).getCareTasks().find { it.id == taskId } ?: return

        if (notificationsOn && task.reminderEnabled && !task.isCompleted && TaskSchedule.isDueToday(task)) {
            val title = when {
                task.category.contains("Feed", ignoreCase = true) -> "Meal time"
                task.category.contains("Exercise", ignoreCase = true) || task.category.contains("Walk", ignoreCase = true) -> "Walk time"
                task.category.contains("Med", ignoreCase = true) -> "Medication reminder"
                task.category.contains("Groom", ignoreCase = true) -> "Grooming time"
                task.category.contains("Clean", ignoreCase = true) -> "Cleaning time"
                task.category.contains("Health", ignoreCase = true) -> "Health check"
                else -> "Care reminder"
            }
            NotificationHelper(context).showTaskNotification(taskId, title, "${task.petName}: ${task.description}")
        }
        // Keep the routine's reminders going: daily → tomorrow, weekly → next chosen day, monthly → next month.
        TaskReminder.schedule(context, task)
    }

    companion object {
        /** System broadcasts after which every alarm must be re-created (also listed in the manifest). */
        private val RESCHEDULE_ACTIONS = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED, // "android.intent.action.TIME_SET"
            Intent.ACTION_TIMEZONE_CHANGED,
            "android.app.action.SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED" // Android 12+
        )

        const val EXTRA_TYPE = "TYPE"
        const val EXTRA_TASK_ID = "TASK_ID"
        const val EXTRA_PET_ID = "PET_ID"
        const val EXTRA_PET_NAME = "PET_NAME"
        const val EXTRA_RECORD_ID = "RECORD_ID"
        const val EXTRA_RECORD_TYPE = "RECORD_TYPE"

        const val TYPE_TASK = "TASK"
        const val TYPE_VACCINE = "VACCINE" // Used by VaccineReminder.
        const val TYPE_RECORD = "RECORD"
    }
}
