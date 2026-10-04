package com.example.petcare

/*
 * UI-facing models returned by the repositories (moved here unchanged from the old AuthDatabaseHelper).
 * Screens keep using these, so the Room migration didn't require changing any adapter or layout code.
 */

data class PetOption(
    val id: Long,
    val name: String
)

data class ExpenseTransaction(
    val id: Long,
    val petId: Long,
    val petName: String,
    val category: String,
    val description: String,
    val date: String,
    val amount: Double
)

/** One routine completed on one day ("yyyy-MM-dd"), for the pet history screen. */
data class CompletionEntry(
    val taskId: Long,
    val description: String,
    val category: String,
    val date: String
)

data class CareTask(
    val id: Long,
    val petId: Long,
    val petName: String,
    val description: String,
    val category: String,
    val repeatType: String,
    val scheduledTime: String,
    val isCompleted: Boolean,
    val weekDays: String = "",
    val completedWeekDays: String = "",
    val reminderEnabled: Boolean = false,
    val avatarUri: String? = null,
    val requiredSupplies: String = "",
    val taskNotes: String = "",
    val linkedLocationId: Long = -1L,
    val locationName: String? = null,
    val locationAddress: String? = null
)

data class PetLocation(
    val id: Long,
    val userId: Long,
    val name: String,
    val address: String,
    val category: String,
    val latitude: Double = 0.0,
    val longitude: Double = 0.0
)

data class HealthcareRecord(
    val id: Long,
    val petId: Long,
    val petName: String,
    val type: String,
    val date: String,
    val notes: String
)
