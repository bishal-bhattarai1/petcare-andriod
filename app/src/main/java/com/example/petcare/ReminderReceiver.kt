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

        // Handle Pet Vaccination
        val petId = intent.getLongExtra("PET_ID", -1L)
        val type = intent.getStringExtra("TYPE")
        if (petId != -1L && type == "VACCINE") {
            val petName = intent.getStringExtra("PET_NAME") ?: "Your pet"
            NotificationHelper(context).showTaskNotification(
                petId + 10000, 
                "Vaccination Reminder!", 
                "$petName's vaccination is scheduled for today. Don't forget to check their profile for details."
            )
        }
    }
}
