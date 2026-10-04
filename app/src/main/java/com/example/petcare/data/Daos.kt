package com.example.petcare.data

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update

/*
 * Data access objects. All methods are blocking and must run on a background thread: Room throws
 * if they are called on the main thread, which guarantees no database work blocks the UI.
 *
 * Per-user filtering: every query that reads or changes user data takes the current user's id
 * (`userId`, matched against pets.owner_id or pet_locations.user_id). A null userId means
 * "nobody is signed in", which keeps the old helper's behaviour of not filtering in that case.
 */

// ---------- Query result rows (joined / aggregated columns) ----------

/** One routine plus its pet and linked place, as shown on the Home / Tasks / Checklist screens. */
data class CareTaskRow(
    val id: Long,
    @ColumnInfo(name = "pet_id") val petId: Long?,
    @ColumnInfo(name = "pet_name") val petName: String?,
    val description: String?,
    val category: String?,
    @ColumnInfo(name = "repeat_type") val repeatType: String?,
    @ColumnInfo(name = "scheduled_time") val scheduledTime: String?,
    @ColumnInfo(name = "is_completed") val isCompleted: Int,
    @ColumnInfo(name = "week_days") val weekDays: String?,
    /** Comma-separated completion dates inside the selected week. */
    @ColumnInfo(name = "week_completions") val weekCompletions: String?,
    @ColumnInfo(name = "reminder_enabled") val reminderEnabled: Int?,
    @ColumnInfo(name = "required_supplies") val requiredSupplies: String?,
    @ColumnInfo(name = "task_notes") val taskNotes: String?,
    @ColumnInfo(name = "linked_location_id") val linkedLocationId: Long?,
    @ColumnInfo(name = "loc_name") val locationName: String?,
    @ColumnInfo(name = "loc_addr") val locationAddress: String?
)

data class TaskCountsRow(
    @ColumnInfo(name = "total_tasks") val totalTasks: Int,
    @ColumnInfo(name = "completed_tasks") val completedTasks: Int
)

data class CategoryDoneRow(val category: String?, val completed: Int?)

data class CompletionRow(
    val id: Long,
    val description: String?,
    val category: String?,
    @ColumnInfo(name = "completed_date") val completedDate: String
)

data class ExpenseRow(
    val id: Long,
    @ColumnInfo(name = "pet_id") val petId: Long,
    @ColumnInfo(name = "pet_name") val petName: String?,
    val category: String?,
    val description: String?,
    @ColumnInfo(name = "expense_date") val expenseDate: String?,
    val amount: Double?
)

data class HealthRecordRow(
    val id: Long,
    @ColumnInfo(name = "pet_id") val petId: Long,
    @ColumnInfo(name = "pet_name") val petName: String?,
    val type: String?,
    val date: String?,
    val notes: String?
)

// ---------- DAOs ----------

@Dao
interface UserDao {
    /** ABORT: a duplicate email throws, which the repository reports as "could not create". */
    @Insert(onConflict = OnConflictStrategy.ABORT)
    fun insert(user: UserEntity): Long

    @Query("SELECT * FROM users WHERE email = :email LIMIT 1")
    fun findByEmail(email: String): UserEntity?

    @Query("SELECT id FROM users WHERE email = :email LIMIT 1")
    fun idByEmail(email: String): Long?

    @Query("SELECT id FROM users ORDER BY id ASC LIMIT 1")
    fun firstUserId(): Long?

    @Query("UPDATE users SET name = :name WHERE email = :email")
    fun updateName(email: String, name: String): Int

    @Query("UPDATE users SET password_hash = :hash WHERE email = :email")
    fun updatePasswordHash(email: String, hash: String): Int
}

@Dao
abstract class PetDao {
    @Insert
    abstract fun insert(pet: PetEntity): Long

    @Update
    abstract fun update(pet: PetEntity): Int

    @Query("SELECT * FROM pets WHERE (:userId IS NULL OR owner_id = :userId) ORDER BY id DESC")
    abstract fun petsForUser(userId: Long?): List<PetEntity>

    /** A pet only if it belongs to [userId] (or no user is signed in). */
    @Query("SELECT * FROM pets WHERE id = :petId AND (:userId IS NULL OR owner_id = :userId) LIMIT 1")
    abstract fun petForUser(petId: Long, userId: Long?): PetEntity?

    /** Pets created before accounts existed (owner_id NULL) are given to the signed-in user. */
    @Query("UPDATE pets SET owner_id = :userId WHERE owner_id IS NULL")
    abstract fun adoptOrphans(userId: Long)

    @Query("UPDATE pets SET vaccine_date = :date WHERE id = :petId")
    abstract fun updateVaccineDate(petId: Long, date: String): Int

    @Query("SELECT COUNT(*) AS total_tasks, COALESCE(SUM(is_completed), 0) AS completed_tasks FROM tasks WHERE pet_id = :petId")
    abstract fun taskCounts(petId: Long): TaskCountsRow

    @Query("SELECT category, MAX(is_completed) AS completed FROM tasks WHERE pet_id = :petId GROUP BY category")
    abstract fun categoryStatus(petId: Long): List<CategoryDoneRow>

    // Building blocks for deletePetCascade (kept protected so callers can't skip the transaction).
    @Query("DELETE FROM task_completions WHERE task_id IN (SELECT id FROM tasks WHERE pet_id = :petId)")
    protected abstract fun deleteCompletionsOfPet(petId: Long)

    @Query("DELETE FROM tasks WHERE pet_id = :petId")
    protected abstract fun deleteTasksOfPet(petId: Long)

    @Query("DELETE FROM expenses WHERE pet_id = :petId")
    protected abstract fun deleteExpensesOfPet(petId: Long)

    @Query("DELETE FROM pet_photos WHERE pet_id = :petId")
    protected abstract fun deletePhotosOfPet(petId: Long)

    @Query("DELETE FROM healthcare_history WHERE pet_id = :petId")
    protected abstract fun deleteHealthRecordsOfPet(petId: Long)

    @Query("DELETE FROM pets WHERE id = :petId")
    protected abstract fun deletePetRow(petId: Long): Int

    /**
     * Deletes a pet and everything that belongs to it (completions, routines, expenses, photos,
     * health records) in ONE transaction: either all rows go, or none do.
     * @return true if the pet existed and was deleted.
     */
    @Transaction
    open fun deletePetCascade(petId: Long): Boolean {
        deleteCompletionsOfPet(petId)
        deleteTasksOfPet(petId)
        deleteExpensesOfPet(petId)
        deletePhotosOfPet(petId)
        deleteHealthRecordsOfPet(petId)
        return deletePetRow(petId) > 0
    }
}

@Dao
abstract class PetPhotoDao {
    @Query("SELECT uri FROM pet_photos WHERE pet_id = :petId ORDER BY id ASC")
    abstract fun urisForPet(petId: Long): List<String>

    @Query("DELETE FROM pet_photos WHERE pet_id = :petId")
    protected abstract fun deleteForPet(petId: Long)

    @Insert
    protected abstract fun insertAll(photos: List<PetPhotoEntity>)

    /** Replaces a pet's photo list atomically. */
    @Transaction
    open fun replacePhotos(petId: Long, uris: List<String>) {
        deleteForPet(petId)
        insertAll(uris.map { PetPhotoEntity(petId = petId, uri = it) })
    }
}

@Dao
abstract class TaskDao {
    @Insert
    abstract fun insert(task: TaskEntity): Long

    /**
     * Routines with their completion state for [dateKey] and the completions within the week
     * [weekStart]..[weekEnd]. Optional filters: one pet, and always the signed-in user's pets.
     */
    @Query(
        """
        SELECT
            t.id,
            t.pet_id,
            COALESCE(p.name, 'Pet') AS pet_name,
            t.description,
            t.category,
            t.repeat_type,
            t.scheduled_time,
            EXISTS (
                SELECT 1 FROM task_completions c WHERE c.task_id = t.id AND c.completed_date = :dateKey
            ) AS is_completed,
            t.week_days,
            (
                SELECT GROUP_CONCAT(c.completed_date) FROM task_completions c
                WHERE c.task_id = t.id AND c.completed_date BETWEEN :weekStart AND :weekEnd
            ) AS week_completions,
            t.reminder_enabled,
            t.required_supplies,
            t.task_notes,
            t.linked_location_id,
            l.name AS loc_name,
            l.address AS loc_addr
        FROM tasks t
        LEFT JOIN pets p ON p.id = t.pet_id
        LEFT JOIN pet_locations l ON l.id = t.linked_location_id
        WHERE (:petId IS NULL OR t.pet_id = :petId)
          AND (:userId IS NULL OR p.owner_id = :userId)
        ORDER BY is_completed ASC, t.id DESC
        """
    )
    abstract fun careTasks(petId: Long?, userId: Long?, dateKey: String, weekStart: String, weekEnd: String): List<CareTaskRow>

    /** Weekly routines saved without any days fall back to every day (they were due daily anyway). */
    @Query("UPDATE tasks SET week_days = 'Mon,Tue,Wed,Thu,Fri,Sat,Sun' WHERE repeat_type = 'Weekly' AND (week_days IS NULL OR week_days = '')")
    abstract fun fixEmptyWeeklyTasks()

    /** Returns the task id if it belongs to one of [userId]'s pets, otherwise null. */
    @Query("SELECT t.id FROM tasks t INNER JOIN pets p ON p.id = t.pet_id WHERE t.id = :taskId AND p.owner_id = :userId LIMIT 1")
    abstract fun ownedTaskId(taskId: Long, userId: Long): Long?

    @Query("INSERT OR IGNORE INTO task_completions (task_id, completed_date) VALUES (:taskId, :dateKey)")
    abstract fun insertCompletion(taskId: Long, dateKey: String)

    @Query("DELETE FROM task_completions WHERE task_id = :taskId AND completed_date = :dateKey")
    abstract fun deleteCompletion(taskId: Long, dateKey: String)

    @Query("UPDATE tasks SET is_completed = :flag WHERE id = :taskId")
    abstract fun setCompletedFlag(taskId: Long, flag: Int)

    /** Records (or removes) a completion and keeps the legacy "done today" flag in step, atomically. */
    @Transaction
    open fun setCompletion(taskId: Long, completed: Boolean, dateKey: String, isToday: Boolean) {
        if (completed) insertCompletion(taskId, dateKey) else deleteCompletion(taskId, dateKey)
        if (isToday) setCompletedFlag(taskId, if (completed) 1 else 0)
    }

    @Query(
        """
        UPDATE tasks SET description = :description, scheduled_time = :scheduledTime,
            required_supplies = :supplies, task_notes = :notes
        WHERE id = :taskId
        """
    )
    abstract fun updateDetails(taskId: Long, description: String, scheduledTime: String, supplies: String, notes: String): Int

    @Query("DELETE FROM task_completions WHERE task_id = :taskId")
    protected abstract fun deleteCompletionsOfTask(taskId: Long)

    @Query("DELETE FROM tasks WHERE id = :taskId")
    protected abstract fun deleteTaskRow(taskId: Long): Int

    /** Deletes a routine and its completion history in one transaction. */
    @Transaction
    open fun deleteTaskCascade(taskId: Long): Boolean {
        deleteCompletionsOfTask(taskId)
        return deleteTaskRow(taskId) > 0
    }

    /** Every day each of the pet's routines was completed, newest first. */
    @Query(
        """
        SELECT t.id, t.description, t.category, c.completed_date
        FROM task_completions c
        INNER JOIN tasks t ON t.id = c.task_id
        WHERE t.pet_id = :petId
        ORDER BY c.completed_date DESC, t.scheduled_time ASC
        """
    )
    abstract fun completionHistory(petId: Long): List<CompletionRow>
}

@Dao
interface ExpenseDao {
    @Insert
    fun insert(expense: ExpenseEntity): Long

    /** Expenses of the user's pets, optionally for one pet and/or matching [query] (description or pet name). */
    @Query(
        """
        SELECT e.id, e.pet_id, COALESCE(p.name, 'Pet') AS pet_name, e.category, e.description,
               e.expense_date, e.amount
        FROM expenses e
        LEFT JOIN pets p ON p.id = e.pet_id
        WHERE (:petId IS NULL OR e.pet_id = :petId)
          AND (:userId IS NULL OR p.owner_id = :userId)
          AND (:query IS NULL OR e.description LIKE '%' || :query || '%' OR p.name LIKE '%' || :query || '%')
        ORDER BY e.created_at DESC, e.id DESC
        """
    )
    fun expenses(petId: Long?, userId: Long?, query: String?): List<ExpenseRow>

    /** Deletes an expense only if it belongs to one of [userId]'s pets. */
    @Query(
        """
        DELETE FROM expenses WHERE id = :expenseId
          AND (:userId IS NULL OR pet_id IN (SELECT id FROM pets WHERE owner_id = :userId))
        """
    )
    fun deleteForUser(expenseId: Long, userId: Long?): Int
}

@Dao
interface HealthRecordDao {
    @Insert
    fun insert(record: HealthRecordEntity): Long

    @Query(
        """
        SELECT h.id, h.pet_id, p.name AS pet_name, h.type, h.date, h.notes
        FROM healthcare_history h
        INNER JOIN pets p ON h.pet_id = p.id
        WHERE h.pet_id = :petId AND (:userId IS NULL OR p.owner_id = :userId)
        ORDER BY h.date DESC
        """
    )
    fun forPet(petId: Long, userId: Long?): List<HealthRecordRow>

    @Query(
        """
        SELECT h.id, h.pet_id, p.name AS pet_name, h.type, h.date, h.notes
        FROM healthcare_history h
        INNER JOIN pets p ON h.pet_id = p.id
        WHERE p.owner_id = :userId
        ORDER BY h.date DESC
        """
    )
    fun forUser(userId: Long): List<HealthRecordRow>

    @Query(
        """
        DELETE FROM healthcare_history WHERE id = :recordId
          AND (:userId IS NULL OR pet_id IN (SELECT id FROM pets WHERE owner_id = :userId))
        """
    )
    fun deleteForUser(recordId: Long, userId: Long?): Int
}

@Dao
interface PlaceDao {
    @Insert
    fun insert(place: PlaceEntity): Long

    @Query("SELECT * FROM pet_locations WHERE (:userId IS NULL OR user_id = :userId) ORDER BY id DESC")
    fun forUser(userId: Long?): List<PlaceEntity>

    @Query("UPDATE pet_locations SET latitude = :latitude, longitude = :longitude WHERE id = :placeId AND (:userId IS NULL OR user_id = :userId)")
    fun updateCoordinates(placeId: Long, latitude: Double, longitude: Double, userId: Long?): Int

    @Query("DELETE FROM pet_locations WHERE id = :placeId AND (:userId IS NULL OR user_id = :userId)")
    fun deleteForUser(placeId: Long, userId: Long?): Int
}
