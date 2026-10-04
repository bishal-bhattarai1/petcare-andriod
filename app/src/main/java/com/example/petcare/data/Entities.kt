package com.example.petcare.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/*
 * Room entities for the 8 tables that already existed in the SQLiteOpenHelper database (version 14).
 * Table names, column names, SQLite types, nullability and default values match the old CREATE TABLE
 * statements exactly, so existing rows map 1:1 (see PetCareDatabase.MIGRATION_14_15).
 *
 * Foreign keys: the old tables declared FOREIGN KEY clauses but the app never ran
 * "PRAGMA foreign_keys = ON", so they were never enforced and real databases contain orphan rows
 * (e.g. an expense whose pet was deleted). Room would enforce them and run a foreign-key check after
 * migrating, which would crash or force deleting user data. The relationships are therefore kept as
 * indexed columns, and cascading deletes are done explicitly in @Transaction DAO methods
 * (PetDao.deletePetCascade, TaskDao.deleteTaskCascade), exactly like the old helper did.
 *
 * Nullable Kotlin types = nullable columns. Flags stay Int (0/1) because that is what is stored.
 */

/** Registered accounts. password_hash holds a PBKDF2 string, a legacy SHA-256 hex, or a placeholder. */
@Entity(tableName = "users", indices = [Index(value = ["email"], unique = true)])
data class UserEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val email: String,
    @ColumnInfo(name = "password_hash") val passwordHash: String,
    @ColumnInfo(name = "created_at") val createdAt: Long
)

/** Pet profiles; owner_id = users.id of the account that owns the pet. */
@Entity(tableName = "pets", indices = [Index("owner_id")])
data class PetEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val species: String? = null,
    val breed: String? = null,
    val age: Int? = null,
    val weight: Double? = null,
    val diet: String? = null,
    @ColumnInfo(name = "vaccine_date") val vaccineDate: String? = null,
    @ColumnInfo(name = "reminder_enabled") val reminderEnabled: Int? = null,
    val allergies: String? = null,
    val toys: String? = null,
    val notes: String? = null,
    @ColumnInfo(name = "owner_id") val ownerId: Long? = null,
    @ColumnInfo(name = "created_at") val createdAt: Long? = null
)

/** Care routines (feeding, walks, meds, ...) belonging to a pet. */
@Entity(tableName = "tasks", indices = [Index("pet_id")])
data class TaskEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "pet_id") val petId: Long? = null,
    val description: String,
    @ColumnInfo(defaultValue = "'Feeding'") val category: String? = "Feeding",
    @ColumnInfo(name = "repeat_type", defaultValue = "'Daily'") val repeatType: String? = "Daily",
    @ColumnInfo(name = "week_days") val weekDays: String? = null,
    @ColumnInfo(name = "scheduled_time") val scheduledTime: String? = null,
    @ColumnInfo(name = "ends_on") val endsOn: String? = null,
    @ColumnInfo(name = "delegate_enabled", defaultValue = "0") val delegateEnabled: Int? = 0,
    /** Legacy "done today" flag, kept in sync with task_completions on every open. */
    @ColumnInfo(name = "is_completed", defaultValue = "0") val isCompleted: Int? = 0,
    @ColumnInfo(name = "expense_amount", defaultValue = "0.0") val expenseAmount: Double? = 0.0,
    @ColumnInfo(name = "completed_week_days", defaultValue = "''") val completedWeekDays: String? = "",
    @ColumnInfo(name = "reminder_enabled", defaultValue = "0") val reminderEnabled: Int? = 0,
    @ColumnInfo(name = "required_supplies", defaultValue = "''") val requiredSupplies: String? = "",
    @ColumnInfo(name = "task_notes", defaultValue = "''") val taskNotes: String? = "",
    /** pet_locations.id of a saved place, or -1 for none. */
    @ColumnInfo(name = "linked_location_id", defaultValue = "-1") val linkedLocationId: Long? = -1
)

/** One row per routine per day it was completed ("yyyy-MM-dd"). */
@Entity(tableName = "task_completions", primaryKeys = ["task_id", "completed_date"])
data class TaskCompletionEntity(
    @ColumnInfo(name = "task_id") val taskId: Long,
    @ColumnInfo(name = "completed_date") val completedDate: String
)

/** Money spent on a pet. */
@Entity(tableName = "expenses", indices = [Index("pet_id")])
data class ExpenseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "pet_id") val petId: Long,
    val category: String,
    val description: String,
    @ColumnInfo(name = "expense_date") val expenseDate: String,
    @ColumnInfo(defaultValue = "0.0") val amount: Double? = 0.0,
    @ColumnInfo(name = "created_at") val createdAt: Long
)

/** Photo content URIs for a pet; the first one is used as the avatar. */
@Entity(tableName = "pet_photos", indices = [Index("pet_id")])
data class PetPhotoEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "pet_id") val petId: Long,
    val uri: String
)

/** Vaccinations, vet visits and other health records. */
@Entity(tableName = "healthcare_history", indices = [Index("pet_id")])
data class HealthRecordEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "pet_id") val petId: Long,
    val type: String,
    val date: String,
    val notes: String? = null
)

/** Geotagged places (vet, groomer, park) saved by a user. */
@Entity(tableName = "pet_locations", indices = [Index("user_id")])
data class PlaceEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "user_id") val userId: Long,
    val name: String,
    val address: String,
    val category: String,
    @ColumnInfo(defaultValue = "0.0") val latitude: Double? = 0.0,
    @ColumnInfo(defaultValue = "0.0") val longitude: Double? = 0.0
)
