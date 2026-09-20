package com.example.petcare

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class ReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val taskId = intent.getLongExtra("TASK_ID", -1L)
        if (taskId == -1L) return

        val database = AuthDatabaseHelper(context)
        val task = database.getCareTasks().find { it.id == taskId } ?: return

        if (!task.isCompleted && task.reminderEnabled) {
            val notificationHelper = NotificationHelper(context)
            
            val title = when {
                task.category.contains("Groom", ignoreCase = true) -> "Grooming Time!"
                task.category.contains("Feed", ignoreCase = true) -> "Meal Time!"
                task.category.contains("Walk", ignoreCase = true) -> "Walkie Time!"
                task.category.contains("Med", ignoreCase = true) -> "Medication Reminder!"
                else -> "Task Reminder!"
            }
            
            val message = "${task.petName} is ready for: ${task.description}"
            notificationHelper.showTaskNotification(taskId, title, message)
        }
    }
}
