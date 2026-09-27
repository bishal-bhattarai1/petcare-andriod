package com.example.petcare

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Shows reminder notifications:
 *  - routines (feeding, walks, medication, grooming…), then schedules the next occurrence,
 *  - vaccinations due today,
 *  - upcoming health records such as vet visits.
 * Also restores every alarm after the phone restarts (alarms don't survive a reboot).
 */
class ReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED || intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            rescheduleAll(context)
            return
        }

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
        val task = AuthDatabaseHelper(context).getCareTasks().find { it.id == taskId } ?: return

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

    /** After a reboot or app update, set every reminder again. */
    private fun rescheduleAll(context: Context) {
        val database = AuthDatabaseHelper(context)
        database.getCareTasks().forEach { TaskReminder.schedule(context, it) }

        database.getPetOptions().forEach { pet ->
            val values = database.getPetById(pet.id) ?: return@forEach
            val date = values.getAsString("vaccine_date").orEmpty()
            if (date.isNotBlank() && values.getAsInteger("reminder_enabled") == 1) {
                VaccineReminder.schedule(context, pet.id, pet.name, date)
            }
        }

        database.getAllHealthcareHistory().forEach { record ->
            RecordReminder.schedule(context, record.id, record.petName, record.type, record.date)
        }
    }

    companion object {
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
