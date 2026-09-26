package com.example.petcare

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.security.MessageDigest

data class TaskStats(
    val totalTasks: Int,
    val completedTasks: Int,
    val totalExpense: Double
)

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

data class PetPhoto(
    val id: Long,
    val petId: Long,
    val uri: String
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

class AuthDatabaseHelper(context: Context) :
    SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    private val appContext: Context = context.applicationContext

    override fun onCreate(db: SQLiteDatabase) {
        createUsersTable(db)
        createPetsTable(db)
        createTasksTable(db)
        createExpensesTable(db)
        createPhotosTable(db)
        createLocationsTable(db)
        createHealthcareTable(db)
        createTaskCompletionsTable(db)
    }

    override fun onOpen(db: SQLiteDatabase) {
        super.onOpen(db)
        if (!db.isReadOnly) {
            createTaskCompletionsTable(db)
            syncTodayCompletionFlags(db)
        }
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 3) {
            db.execSQL("DROP TABLE IF EXISTS pets")
            db.execSQL("DROP TABLE IF EXISTS tasks")
            createUsersTable(db)
            createPetsTable(db)
            createTasksTable(db)
            createExpensesTable(db)
        }

        if (oldVersion < 4) {
            addTaskScheduleColumns(db)
        }

        if (oldVersion < 5) {
            createExpensesTable(db)
        }

        if (oldVersion < 6) {
            addPetOwnerColumn(db)
        }

        if (oldVersion < 7) {
            addCompletedWeekDaysColumn(db)
        }

        if (oldVersion < 8) {
            addReminderEnabledColumn(db)
        }

        if (oldVersion < 9) {
            createPhotosTable(db)
            createLocationsTable(db)
            createHealthcareTable(db)
        }

        if (oldVersion < 10) {
            addTaskSuppliesAndNotesColumns(db)
        }

        if (oldVersion < 11) {
            addTaskLocationColumn(db)
        }

        if (oldVersion < 12) {
            addPetCreatedAtColumn(db)
        }

        if (oldVersion < 13) {
            ensureLocationsTableExists(db)
        }

        if (oldVersion < 14) {
            createTaskCompletionsTable(db)
            // Carry over anything already ticked off as completed today.
            db.execSQL(
                "INSERT OR IGNORE INTO task_completions (task_id, completed_date) SELECT id, ? FROM tasks WHERE is_completed = 1",
                arrayOf<Any>(todayKey())
            )
        }
    }

    private fun addPetCreatedAtColumn(db: SQLiteDatabase) {
        try {
            db.execSQL("ALTER TABLE pets ADD COLUMN created_at INTEGER")
        } catch (_: Exception) {
        }
    }

    private fun addTaskLocationColumn(db: SQLiteDatabase) {
        try {
            db.execSQL("ALTER TABLE tasks ADD COLUMN linked_location_id INTEGER DEFAULT -1")
        } catch (_: Exception) {
        }
    }

    private fun addTaskSuppliesAndNotesColumns(db: SQLiteDatabase) {
        try {
            db.execSQL("ALTER TABLE tasks ADD COLUMN required_supplies TEXT DEFAULT ''")
            db.execSQL("ALTER TABLE tasks ADD COLUMN task_notes TEXT DEFAULT ''")
        } catch (_: Exception) {
        }
    }

    private fun createPhotosTable(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS pet_photos (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                pet_id INTEGER NOT NULL,
                uri TEXT NOT NULL,
                FOREIGN KEY(pet_id) REFERENCES pets(id) ON DELETE CASCADE
            )
            """.trimIndent()
        )
    }

    private fun createLocationsTable(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS pet_locations (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                user_id INTEGER NOT NULL,
                name TEXT NOT NULL,
                address TEXT NOT NULL,
                category TEXT NOT NULL,
                latitude REAL DEFAULT 0.0,
                longitude REAL DEFAULT 0.0,
                FOREIGN KEY(user_id) REFERENCES $TABLE_USERS($COLUMN_ID) ON DELETE CASCADE
            )
            """.trimIndent()
        )
        addLocationCoordinatesColumns(db)
    }

    private fun addLocationCoordinatesColumns(db: SQLiteDatabase) {
        try {
            db.execSQL("ALTER TABLE pet_locations ADD COLUMN latitude REAL DEFAULT 0.0")
        } catch (_: Exception) {
        }
        try {
            db.execSQL("ALTER TABLE pet_locations ADD COLUMN longitude REAL DEFAULT 0.0")
        } catch (_: Exception) {
        }
    }

    private fun createHealthcareTable(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS healthcare_history (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                pet_id INTEGER NOT NULL,
                type TEXT NOT NULL,
                date TEXT NOT NULL,
                notes TEXT,
                FOREIGN KEY(pet_id) REFERENCES pets(id) ON DELETE CASCADE
            )
            """.trimIndent()
        )
    }

    private fun addReminderEnabledColumn(db: SQLiteDatabase) {
        try {
            db.execSQL("ALTER TABLE tasks ADD COLUMN reminder_enabled INTEGER DEFAULT 0")
        } catch (_: Exception) {
        }
    }

    private fun addCompletedWeekDaysColumn(db: SQLiteDatabase) {
        try {
            db.execSQL("ALTER TABLE tasks ADD COLUMN completed_week_days TEXT DEFAULT ''")
        } catch (_: Exception) {
        }
    }

    fun createUser(name: String, email: String, password: String): Boolean {
        val values = ContentValues().apply {
            put(COLUMN_NAME, name.trim())
            put(COLUMN_EMAIL, email.normalizedEmail())
            put(COLUMN_PASSWORD_HASH, password.hashForEmail(email))
            put(COLUMN_CREATED_AT, System.currentTimeMillis())
        }

        return try {
            writableDatabase.insertOrThrow(TABLE_USERS, null, values)
            true
        } catch (_: Exception) {
            false
        }
    }

    fun createSocialUser(name: String, email: String): Boolean {
        val normalized = email.normalizedEmail()
        if (emailExists(normalized)) return true

        val values = ContentValues().apply {
            put(COLUMN_NAME, name.trim())
            put(COLUMN_EMAIL, normalized)
            put(COLUMN_PASSWORD_HASH, "SOCIAL_LOGIN_NO_PASSWORD")
            put(COLUMN_CREATED_AT, System.currentTimeMillis())
        }

        return try {
            writableDatabase.insertOrThrow(TABLE_USERS, null, values)
            true
        } catch (_: Exception) {
            false
        }
    }

    fun getUserName(email: String, password: String): String? {
        val cursor = readableDatabase.query(
            TABLE_USERS,
            arrayOf(COLUMN_NAME),
            "$COLUMN_EMAIL = ? AND $COLUMN_PASSWORD_HASH = ?",
            arrayOf(email.normalizedEmail(), password.hashForEmail(email)),
            null,
            null,
            null,
            "1"
        )

        return cursor.use {
            if (it.moveToFirst()) it.getString(it.getColumnIndexOrThrow(COLUMN_NAME)) else null
        }
    }

    fun getUserNameByEmail(email: String): String? {
        val cursor = readableDatabase.query(
            TABLE_USERS,
            arrayOf(COLUMN_NAME),
            "$COLUMN_EMAIL = ?",
            arrayOf(email.normalizedEmail()),
            null,
            null,
            null,
            "1"
        )

        return cursor.use {
            if (it.moveToFirst()) it.getString(it.getColumnIndexOrThrow(COLUMN_NAME)) else null
        }
    }

    fun emailExists(email: String): Boolean {
        val cursor = readableDatabase.query(
            TABLE_USERS,
            arrayOf(COLUMN_ID),
            "$COLUMN_EMAIL = ?",
            arrayOf(email.normalizedEmail()),
            null,
            null,
            null,
            "1"
        )

        return cursor.use { it.moveToFirst() }
    }

    fun updateUserName(email: String, newName: String): Boolean {
        val values = ContentValues().apply {
            put(COLUMN_NAME, newName.trim())
        }

        return try {
            val rows = writableDatabase.update(
                TABLE_USERS,
                values,
                "$COLUMN_EMAIL = ?",
                arrayOf(email.normalizedEmail())
            )
            rows > 0
        } catch (_: Exception) {
            false
        }
    }

    fun verifyPassword(email: String, password: String): Boolean {
        val cursor = readableDatabase.query(
            TABLE_USERS,
            arrayOf(COLUMN_ID),
            "$COLUMN_EMAIL = ? AND $COLUMN_PASSWORD_HASH = ?",
            arrayOf(email.normalizedEmail(), password.hashForEmail(email)),
            null,
            null,
            null,
            "1"
        )
        return cursor.use { it.moveToFirst() }
    }

    fun updateUserProfile(oldEmail: String, newName: String, newEmail: String, password: String): Boolean {
        val values = ContentValues().apply {
            put(COLUMN_NAME, newName.trim())
            put(COLUMN_EMAIL, newEmail.normalizedEmail())
            put(COLUMN_PASSWORD_HASH, password.hashForEmail(newEmail))
        }

        return try {
            val rows = writableDatabase.update(
                TABLE_USERS,
                values,
                "$COLUMN_EMAIL = ?",
                arrayOf(oldEmail.normalizedEmail())
            )
            rows > 0
        } catch (_: Exception) {
            false
        }
    }

    fun updatePassword(email: String, password: String): Boolean {
        val values = ContentValues().apply {
            put(COLUMN_PASSWORD_HASH, password.hashForEmail(email))
        }

        return try {
            val rows = writableDatabase.update(
                TABLE_USERS,
                values,
                "$COLUMN_EMAIL = ?",
                arrayOf(email.normalizedEmail())
            )
            rows > 0
        } catch (_: Exception) {
            false
        }
    }

    fun savePet(
        name: String,
        species: String,
        breed: String,
        age: Int,
        weight: Double,
        diet: String,
        vaccine: String,
        reminder: Boolean,
        allergies: String,
        toys: String,
        notes: String
    ): Long {
        val values = ContentValues().apply {
            put("name", name)
            put("species", species)
            put("breed", breed)
            put("age", age)
            put("weight", weight)
            put("diet", diet)
            put("vaccine_date", vaccine)
            put("reminder_enabled", if (reminder) 1 else 0)
            put("allergies", allergies)
            put("toys", toys)
            put("notes", notes)
            put("created_at", System.currentTimeMillis())
            currentUserId()?.let { put("owner_id", it) }
        }

        val petId = try {
            writableDatabase.insert("pets", null, values)
        } catch (_: Exception) {
            -1L
        }
        if (petId != -1L) seedDefaultTasks(petId)
        return petId
    }

    fun getAllPets(): List<PetDashboardModel> {
        adoptOrphanPetsForCurrentUser()
        val pets = mutableListOf<PetDashboardModel>()
        val userId = currentUserId()
        val cursor = readableDatabase.query(
            "pets",
            null,
            userId?.let { "owner_id = ?" },
            userId?.let { arrayOf(it.toString()) },
            null,
            null,
            "id DESC"
        )
        cursor.use {
            while (it.moveToNext()) {
                val petId = it.getLong(it.getColumnIndexOrThrow("id"))
                val name = it.getStringOrEmpty("name")
                val species = it.getStringOrEmpty("species")
                val breed = it.getStringOrEmpty("breed")
                val age = it.getIntOrZero("age")
                val weight = it.getDoubleOrZero("weight")
                val diet = it.getStringOrEmpty("diet")
                val vaccineDate = it.getStringOrEmpty("vaccine_date")
                val allergies = it.getStringOrEmpty("allergies")
                val toys = it.getStringOrEmpty("toys")
                val notes = it.getStringOrEmpty("notes")
                val createdAt = it.getLong(it.getColumnIndexOrThrow("created_at"))
                
                val progress = calculateProfileProgress(
                    name,
                    species,
                    breed,
                    age,
                    weight,
                    diet,
                    vaccineDate,
                    allergies,
                    toys,
                    notes
                )
                val completedTasks = progress / 25
                val displayBreed = listOf(breed, species)
                    .filter { value -> value.isNotBlank() && value != "Unknown" }
                    .joinToString(" - ")
                    .ifBlank { "Pet profile" }
                val taskCounts = getPetTaskCounts(petId)
                val categoryStatus = getPetCategoryStatus(petId)
                
                val taskProgress = if (taskCounts.totalTasks == 0) {
                    0
                } else {
                    (taskCounts.completedTasks * 100) / taskCounts.totalTasks
                }
                val pendingTasks = taskCounts.totalTasks - taskCounts.completedTasks
                val taskStatus = when {
                    pendingTasks > 0 -> "$pendingTasks care ${if (pendingTasks == 1) "task" else "tasks"} pending"
                    taskCounts.totalTasks > 0 -> "All care tasks done"
                    vaccineDate.isNotBlank() -> "Upcoming: $vaccineDate"
                    else -> null
                }

                val model = PetDashboardModel(
                    id = petId,
                    name = name,
                    breed = displayBreed,
                    progress = taskProgress,
                    totalTasks = taskCounts.totalTasks,
                    completedTasks = taskCounts.completedTasks,
                    statusAlert = taskStatus,
                    isCritical = pendingTasks > 0,
                    createdAt = createdAt,
                    isFed = categoryStatus.isFed,
                    isWalked = categoryStatus.isWalked,
                    isMedsTaken = categoryStatus.isMedsTaken,
                    isGroomed = categoryStatus.isGroomed
                )
                
                // Fetch first photo for avatar
                val photos = getPetPhotos(petId)
                val modelWithAvatar = model.copy(avatarUri = photos.firstOrNull())

                pets.add(modelWithAvatar)
            }
        }
        return pets
    }

    fun getPetOptions(): List<PetOption> {
        adoptOrphanPetsForCurrentUser()
        val pets = mutableListOf<PetOption>()
        val userId = currentUserId()
        val cursor = readableDatabase.query(
            "pets",
            arrayOf("id", "name"),
            userId?.let { "owner_id = ?" },
            userId?.let { arrayOf(it.toString()) },
            null,
            null,
            "id DESC"
        )

        cursor.use {
            while (it.moveToNext()) {
                val petId = it.getLong(it.getColumnIndexOrThrow("id"))
                val name = it.getStringOrEmpty("name").ifBlank { "Unnamed pet" }
                pets.add(PetOption(id = petId, name = name))
            }
        }

        return pets
    }

    fun getPetById(petId: Long): android.content.ContentValues? {
        val cursor = readableDatabase.query("pets", null, "id = ?", arrayOf(petId.toString()), null, null, null)
        return cursor.use {
            if (it.moveToFirst()) {
                val values = ContentValues()
                android.database.DatabaseUtils.cursorRowToContentValues(it, values)
                values
            } else null
        }
    }

    fun updatePet(
        petId: Long,
        name: String,
        species: String,
        breed: String,
        age: Int,
        weight: Double,
        diet: String,
        vaccine: String,
        reminder: Boolean,
        allergies: String,
        toys: String,
        notes: String
    ): Boolean {
        val values = ContentValues().apply {
            put("name", name)
            put("species", species)
            put("breed", breed)
            put("age", age)
            put("weight", weight)
            put("diet", diet)
            put("vaccine_date", vaccine)
            put("reminder_enabled", if (reminder) 1 else 0)
            put("allergies", allergies)
            put("toys", toys)
            put("notes", notes)
        }

        return try {
            writableDatabase.update("pets", values, "id = ?", arrayOf(petId.toString())) > 0
        } catch (_: Exception) {
            false
        }
    }

    fun saveTask(
        petId: Long,
        description: String,
        expenseAmount: Double,
        category: String = "Feeding",
        repeatType: String = "Daily",
        weekDays: String = "",
        scheduledTime: String = "",
        endsOn: String = "",
        delegate: Boolean = false,
        reminder: Boolean = false,
        supplies: String = "",
        notes: String = "",
        locationId: Long = -1L
    ): Long {
        adoptOrphanPetsForCurrentUser()
        if (!canAccessPet(petId)) return -1L

        val values = ContentValues().apply {
            put("pet_id", petId)
            put("description", description)
            put("expense_amount", expenseAmount)
            put("category", category)
            put("repeat_type", repeatType)
            put("week_days", weekDays)
            put("scheduled_time", scheduledTime)
            put("ends_on", endsOn)
            put("delegate_enabled", if (delegate) 1 else 0)
            put("reminder_enabled", if (reminder) 1 else 0)
            put("required_supplies", supplies)
            put("task_notes", notes)
            put("linked_location_id", locationId)
        }

        return try {
            writableDatabase.insert("tasks", null, values)
        } catch (_: Exception) {
            -1L
        }
    }

    // Seeds starter routines once, when a pet is created. Running this on every read
    // would resurrect the defaults whenever a user deleted all of a pet's tasks.
    private fun seedDefaultTasks(petId: Long) {
        listOf(
            arrayOf("Morning Kibble & Wet Food", "Feeding", "7:30 AM", "1 can salmon blend", "Fresh water bowl"),
            arrayOf("Afternoon Fur Brushing", "Grooming", "2:00 PM", "Soft slicker brush", "Balcony rug"),
            arrayOf("Ear Drops & Multivitamin", "Health", "6:00 PM", "2 drops left ear + chewable", "Give after meal"),
            arrayOf("Evening Laser Chase", "Activity", "8:00 PM", "20 mins indoor cardio", "Play before bedtime")
        ).forEach { (description, category, time, supplies, notes) ->
            saveTask(
                petId = petId,
                description = description,
                expenseAmount = 0.0,
                category = category,
                repeatType = "Daily",
                scheduledTime = time,
                supplies = supplies,
                notes = notes
            )
        }
    }

    fun getCareTasks(petId: Long? = null, dateKey: String = todayKey()): List<CareTask> {
        adoptOrphanPetsForCurrentUser()
        val tasks = mutableListOf<CareTask>()
        val clauses = mutableListOf<String>()
        val args = mutableListOf(dateKey)
        petId?.let {
            clauses.add("t.pet_id = ?")
            args.add(it.toString())
        }
        currentUserId()?.let {
            clauses.add("p.owner_id = ?")
            args.add(it.toString())
        }
        val whereClause = if (clauses.isEmpty()) "" else "WHERE ${clauses.joinToString(" AND ")}"
        val cursor = readableDatabase.rawQuery(
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
                    SELECT 1 FROM task_completions c WHERE c.task_id = t.id AND c.completed_date = ?
                ) AS is_completed,
                t.week_days,
                t.completed_week_days,
                t.reminder_enabled,
                t.required_supplies,
                t.task_notes,
                t.linked_location_id,
                l.name as loc_name,
                l.address as loc_addr
            FROM tasks t
            LEFT JOIN pets p ON p.id = t.pet_id
            LEFT JOIN pet_locations l ON l.id = t.linked_location_id
            $whereClause
            ORDER BY is_completed ASC, t.id DESC
            """.trimIndent(),
            args.toTypedArray()
        )

        cursor.use {
            while (it.moveToNext()) {
                tasks.add(
                    CareTask(
                        id = it.getLong(it.getColumnIndexOrThrow("id")),
                        petId = it.getLong(it.getColumnIndexOrThrow("pet_id")),
                        petName = it.getStringOrEmpty("pet_name").ifBlank { "Pet" },
                        description = it.getStringOrEmpty("description"),
                        category = it.getStringOrEmpty("category").ifBlank { "Care" },
                        repeatType = it.getStringOrEmpty("repeat_type").ifBlank { "Daily" },
                        scheduledTime = it.getStringOrEmpty("scheduled_time"),
                        isCompleted = it.getIntOrZero("is_completed") == 1,
                        weekDays = it.getStringOrEmpty("week_days"),
                        completedWeekDays = it.getStringOrEmpty("completed_week_days"),
                        reminderEnabled = it.getIntOrZero("reminder_enabled") == 1,
                        requiredSupplies = it.getStringOrEmpty("required_supplies"),
                        taskNotes = it.getStringOrEmpty("task_notes"),
                        linkedLocationId = it.getLong(it.getColumnIndexOrThrow("linked_location_id")),
                        locationName = it.getStringOrEmpty("loc_name"),
                        locationAddress = it.getStringOrEmpty("loc_addr")
                    )
                )
            }
        }

        return tasks
    }

    fun updateTaskCompletion(taskId: Long, completed: Boolean, dateKey: String = todayKey()): Boolean {
        if (!canAccessTask(taskId)) return false

        return try {
            val db = writableDatabase
            if (completed) {
                db.execSQL(
                    "INSERT OR IGNORE INTO task_completions (task_id, completed_date) VALUES (?, ?)",
                    arrayOf<Any>(taskId, dateKey)
                )
            } else {
                db.delete(
                    "task_completions",
                    "task_id = ? AND completed_date = ?",
                    arrayOf(taskId.toString(), dateKey)
                )
            }
            if (dateKey == todayKey()) {
                val values = ContentValues().apply {
                    put("is_completed", if (completed) 1 else 0)
                }
                db.update("tasks", values, "id = ?", arrayOf(taskId.toString()))
            }
            true
        } catch (_: Exception) {
            false
        }
    }

    fun updateTaskDetails(taskId: Long, description: String, scheduledTime: String, supplies: String, notes: String): Boolean {
        if (!canAccessTask(taskId)) return false

        val values = ContentValues().apply {
            put("description", description)
            put("scheduled_time", scheduledTime)
            put("required_supplies", supplies)
            put("task_notes", notes)
        }

        return try {
            writableDatabase.update(
                "tasks",
                values,
                "id = ?",
                arrayOf(taskId.toString())
            ) > 0
        } catch (_: Exception) {
            false
        }
    }

    fun deleteTask(taskId: Long): Boolean {
        if (!canAccessTask(taskId)) return false
        return try {
            writableDatabase.delete("task_completions", "task_id = ?", arrayOf(taskId.toString()))
            writableDatabase.delete("tasks", "id = ?", arrayOf(taskId.toString())) > 0
        } catch (_: Exception) {
            false
        }
    }

    fun deletePet(petId: Long): Boolean {
        if (!canAccessPet(petId)) return false
        val db = writableDatabase
        val args = arrayOf(petId.toString())
        db.beginTransaction()
        return try {
            db.delete("task_completions", "task_id IN (SELECT id FROM tasks WHERE pet_id = ?)", args)
            db.delete("tasks", "pet_id = ?", args)
            db.delete("expenses", "pet_id = ?", args)
            db.delete("pet_photos", "pet_id = ?", args)
            db.delete("healthcare_history", "pet_id = ?", args)
            val deleted = db.delete("pets", "id = ?", args) > 0
            db.setTransactionSuccessful()
            deleted
        } catch (_: Exception) {
            false
        } finally {
            db.endTransaction()
        }
    }

    fun updateWeeklyDayCompletion(taskId: Long, completedDays: String): Boolean {
        if (!canAccessTask(taskId)) return false

        val values = ContentValues().apply {
            put("completed_week_days", completedDays)
        }

        return try {
            writableDatabase.update(
                "tasks",
                values,
                "id = ?",
                arrayOf(taskId.toString())
            ) > 0
        } catch (_: Exception) {
            false
        }
    }

    fun saveExpense(
        petId: Long,
        category: String,
        description: String,
        date: String,
        amount: Double
    ): Boolean {
        adoptOrphanPetsForCurrentUser()
        if (!canAccessPet(petId)) return false

        val values = ContentValues().apply {
            put("pet_id", petId)
            put("category", category)
            put("description", description)
            put("expense_date", date)
            put("amount", amount)
            put("created_at", System.currentTimeMillis())
        }

        return try {
            writableDatabase.insert("expenses", null, values) != -1L
        } catch (_: Exception) {
            false
        }
    }

    fun getExpenses(petId: Long? = null, query: String? = null): List<ExpenseTransaction> {
        adoptOrphanPetsForCurrentUser()
        val expenses = mutableListOf<ExpenseTransaction>()
        val clauses = mutableListOf<String>()
        val args = mutableListOf<String>()
        
        petId?.let {
            clauses.add("e.pet_id = ?")
            args.add(it.toString())
        }
        
        currentUserId()?.let {
            clauses.add("p.owner_id = ?")
            args.add(it.toString())
        }

        if (!query.isNullOrBlank()) {
            clauses.add("(e.description LIKE ? OR p.name LIKE ?)")
            args.add("%$query%")
            args.add("%$query%")
        }

        val whereClause = if (clauses.isEmpty()) "" else "WHERE ${clauses.joinToString(" AND ")}"
        val cursor = readableDatabase.rawQuery(
            """
            SELECT
                e.id,
                e.pet_id,
                COALESCE(p.name, 'Pet') AS pet_name,
                e.category,
                e.description,
                e.expense_date,
                e.amount
            FROM expenses e
            LEFT JOIN pets p ON p.id = e.pet_id
            $whereClause
            ORDER BY e.created_at DESC, e.id DESC
            """.trimIndent(),
            args.toTypedArray()
        )

        cursor.use {
            while (it.moveToNext()) {
                expenses.add(
                    ExpenseTransaction(
                        id = it.getLong(it.getColumnIndexOrThrow("id")),
                        petId = it.getLong(it.getColumnIndexOrThrow("pet_id")),
                        petName = it.getStringOrEmpty("pet_name"),
                        category = it.getStringOrEmpty("category"),
                        description = it.getStringOrEmpty("description"),
                        date = it.getStringOrEmpty("expense_date"),
                        amount = it.getDoubleOrZero("amount")
                    )
                )
            }
        }

        return expenses
    }

    fun deleteExpense(expenseId: Long): Boolean {
        return try {
            writableDatabase.delete("expenses", "id = ?", arrayOf(expenseId.toString())) > 0
        } catch (_: Exception) {
            false
        }
    }

    private fun createExpensesTable(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS expenses (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                pet_id INTEGER NOT NULL,
                category TEXT NOT NULL,
                description TEXT NOT NULL,
                expense_date TEXT NOT NULL,
                amount REAL DEFAULT 0.0,
                created_at INTEGER NOT NULL,
                FOREIGN KEY(pet_id) REFERENCES pets(id)
            )
            """.trimIndent()
        )
    }

    private fun createUsersTable(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS $TABLE_USERS (
                $COLUMN_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COLUMN_NAME TEXT NOT NULL,
                $COLUMN_EMAIL TEXT NOT NULL UNIQUE,
                $COLUMN_PASSWORD_HASH TEXT NOT NULL,
                $COLUMN_CREATED_AT INTEGER NOT NULL
            )
            """.trimIndent()
        )
    }

    private fun createPetsTable(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS pets (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                name TEXT NOT NULL,
                species TEXT,
                breed TEXT,
                age INTEGER,
                weight REAL,
                diet TEXT,
                vaccine_date TEXT,
                reminder_enabled INTEGER,
                allergies TEXT,
                toys TEXT,
                notes TEXT,
                owner_id INTEGER,
                created_at INTEGER,
                FOREIGN KEY(owner_id) REFERENCES $TABLE_USERS($COLUMN_ID)
            )
            """.trimIndent()
        )
    }

    private fun createTaskCompletionsTable(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS task_completions (
                task_id INTEGER NOT NULL,
                completed_date TEXT NOT NULL,
                PRIMARY KEY (task_id, completed_date),
                FOREIGN KEY(task_id) REFERENCES tasks(id)
            )
            """.trimIndent()
        )
    }

    /** Keeps the legacy `is_completed` flag meaning "done today", so a new day starts fresh everywhere. */
    private fun syncTodayCompletionFlags(db: SQLiteDatabase) {
        try {
            db.execSQL(
                """
                UPDATE tasks SET is_completed = CASE WHEN EXISTS (
                    SELECT 1 FROM task_completions c WHERE c.task_id = tasks.id AND c.completed_date = ?
                ) THEN 1 ELSE 0 END
                """.trimIndent(),
                arrayOf<Any>(todayKey())
            )
        } catch (_: Exception) {
        }
    }

    private fun createTasksTable(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS tasks (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                pet_id INTEGER,
                description TEXT NOT NULL,
                category TEXT DEFAULT 'Feeding',
                repeat_type TEXT DEFAULT 'Daily',
                week_days TEXT,
                scheduled_time TEXT,
                ends_on TEXT,
                delegate_enabled INTEGER DEFAULT 0,
                is_completed INTEGER DEFAULT 0,
                expense_amount REAL DEFAULT 0.0,
                completed_week_days TEXT DEFAULT '',
                reminder_enabled INTEGER DEFAULT 0,
                required_supplies TEXT DEFAULT '',
                task_notes TEXT DEFAULT '',
                linked_location_id INTEGER DEFAULT -1,
                FOREIGN KEY(pet_id) REFERENCES pets(id)
            )
            """.trimIndent()
        )
    }

    private fun getPetTaskCounts(petId: Long): TaskCounts {
        val cursor = readableDatabase.rawQuery(
            """
            SELECT
                COUNT(*) AS total_tasks,
                COALESCE(SUM(is_completed), 0) AS completed_tasks
            FROM tasks
            WHERE pet_id = ?
            """.trimIndent(),
            arrayOf(petId.toString())
        )

        return cursor.use {
            if (it.moveToFirst()) {
                TaskCounts(
                    totalTasks = it.getInt(it.getColumnIndexOrThrow("total_tasks")),
                    completedTasks = it.getInt(it.getColumnIndexOrThrow("completed_tasks"))
                )
            } else {
                TaskCounts(0, 0)
            }
        }
    }

    private fun getPetCategoryStatus(petId: Long): PetCategoryStatus {
        val cursor = readableDatabase.rawQuery(
            """
            SELECT category, MAX(is_completed) as completed
            FROM tasks
            WHERE pet_id = ?
            GROUP BY category
            """.trimIndent(),
            arrayOf(petId.toString())
        )
        
        var isFed = false
        var isWalked = false
        var isMedsTaken = false
        var isGroomed = false
        
        cursor.use {
            while (it.moveToNext()) {
                val cat = it.getString(it.getColumnIndexOrThrow("category")).lowercase()
                val done = it.getInt(it.getColumnIndexOrThrow("completed")) == 1
                when {
                    cat.contains("feed") || cat.contains("food") -> if (done) isFed = true
                    cat.contains("walk") || cat.contains("exercise") || cat.contains("activity") -> if (done) isWalked = true
                    cat.contains("med") || cat.contains("health") -> if (done) isMedsTaken = true
                    cat.contains("groom") -> if (done) isGroomed = true
                }
            }
        }
        
        return PetCategoryStatus(isFed, isWalked, isMedsTaken, isGroomed)
    }

    private data class PetCategoryStatus(
        val isFed: Boolean,
        val isWalked: Boolean,
        val isMedsTaken: Boolean,
        val isGroomed: Boolean
    )

    private fun addTaskScheduleColumns(db: SQLiteDatabase) {
        listOf(
            "ALTER TABLE tasks ADD COLUMN category TEXT DEFAULT 'Feeding'",
            "ALTER TABLE tasks ADD COLUMN repeat_type TEXT DEFAULT 'Daily'",
            "ALTER TABLE tasks ADD COLUMN week_days TEXT",
            "ALTER TABLE tasks ADD COLUMN scheduled_time TEXT",
            "ALTER TABLE tasks ADD COLUMN ends_on TEXT",
            "ALTER TABLE tasks ADD COLUMN delegate_enabled INTEGER DEFAULT 0"
        ).forEach { statement ->
            try {
                db.execSQL(statement)
            } catch (_: Exception) {
            }
        }
    }

    private fun addPetOwnerColumn(db: SQLiteDatabase) {
        try {
            db.execSQL("ALTER TABLE pets ADD COLUMN owner_id INTEGER")
        } catch (_: Exception) {
        }
    }

    private fun currentUserId(): Long? {
        val email = SessionManager(appContext).getUserEmail().orEmpty().normalizedEmail()
        if (email.isBlank()) return null

        val cursor = readableDatabase.query(
            TABLE_USERS,
            arrayOf(COLUMN_ID),
            "$COLUMN_EMAIL = ?",
            arrayOf(email),
            null,
            null,
            null,
            "1"
        )

        return cursor.use {
            if (it.moveToFirst()) it.getLong(it.getColumnIndexOrThrow(COLUMN_ID)) else null
        }
    }

    private fun canAccessPet(petId: Long): Boolean {
        val userId = currentUserId() ?: return petId > 0
        val cursor = readableDatabase.query(
            "pets",
            arrayOf("id"),
            "id = ? AND owner_id = ?",
            arrayOf(petId.toString(), userId.toString()),
            null,
            null,
            null,
            "1"
        )

        return cursor.use { it.moveToFirst() }
    }

    private fun canAccessTask(taskId: Long): Boolean {
        val userId = currentUserId() ?: return taskId > 0
        val cursor = readableDatabase.rawQuery(
            """
            SELECT t.id
            FROM tasks t
            INNER JOIN pets p ON p.id = t.pet_id
            WHERE t.id = ? AND p.owner_id = ?
            LIMIT 1
            """.trimIndent(),
            arrayOf(taskId.toString(), userId.toString())
        )

        return cursor.use { it.moveToFirst() }
    }

    private fun adoptOrphanPetsForCurrentUser() {
        val userId = currentUserId() ?: return
        val values = ContentValues().apply {
            put("owner_id", userId)
        }

        try {
            writableDatabase.update("pets", values, "owner_id IS NULL", null)
        } catch (_: Exception) {
        }
    }

    fun getTaskStats(): TaskStats {
        adoptOrphanPetsForCurrentUser()
        val userId = currentUserId()
        val cursor = if (userId == null) {
            readableDatabase.rawQuery(
                """
                SELECT
                    COUNT(*) AS total_tasks,
                    COALESCE(SUM(is_completed), 0) AS completed_tasks,
                    COALESCE(SUM(expense_amount), 0) AS total_expense
                FROM tasks
                """.trimIndent(),
                null
            )
        } else {
            readableDatabase.rawQuery(
                """
                SELECT
                    COUNT(t.id) AS total_tasks,
                    COALESCE(SUM(t.is_completed), 0) AS completed_tasks,
                    COALESCE(SUM(t.expense_amount), 0) AS total_expense
                FROM tasks t
                INNER JOIN pets p ON p.id = t.pet_id
                WHERE p.owner_id = ?
                """.trimIndent(),
                arrayOf(userId.toString())
            )
        }

        return cursor.use {
            if (it.moveToFirst()) {
                TaskStats(
                    totalTasks = it.getInt(it.getColumnIndexOrThrow("total_tasks")),
                    completedTasks = it.getInt(it.getColumnIndexOrThrow("completed_tasks")),
                    totalExpense = it.getDouble(it.getColumnIndexOrThrow("total_expense"))
                )
            } else {
                TaskStats(0, 0, 0.0)
            }
        }
    }

    private fun calculateProfileProgress(
        name: String,
        species: String,
        breed: String,
        age: Int,
        weight: Double,
        diet: String,
        vaccineDate: String,
        allergies: String,
        toys: String,
        notes: String
    ): Int {
        val completedFields = listOf(
            name.isNotBlank(),
            species.isNotBlank() && species != "Unknown",
            breed.isNotBlank(),
            age > 0,
            weight > 0.0,
            diet.isNotBlank(),
            vaccineDate.isNotBlank(),
            allergies.isNotBlank(),
            toys.isNotBlank(),
            notes.isNotBlank()
        ).count { it }

        return (completedFields * 100) / 10
    }

    private fun android.database.Cursor.getStringOrEmpty(columnName: String): String {
        val index = getColumnIndexOrThrow(columnName)
        return if (isNull(index)) "" else getString(index).orEmpty()
    }

    private fun android.database.Cursor.getIntOrZero(columnName: String): Int {
        val index = getColumnIndexOrThrow(columnName)
        return if (isNull(index)) 0 else getInt(index)
    }

    private fun android.database.Cursor.getDoubleOrZero(columnName: String): Double {
        val index = getColumnIndexOrThrow(columnName)
        return if (isNull(index)) 0.0 else getDouble(index)
    }

    private data class TaskCounts(
        val totalTasks: Int,
        val completedTasks: Int
    )

    private fun String.normalizedEmail(): String = trim().lowercase()

    private fun String.hashForEmail(email: String): String {
        val saltedPassword = "${email.normalizedEmail()}:$this"
        val bytes = MessageDigest.getInstance("SHA-256").digest(saltedPassword.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }

    fun savePetPhotos(petId: Long, uris: List<String>) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.delete("pet_photos", "pet_id = ?", arrayOf(petId.toString()))
            uris.forEach { uri ->
                val values = ContentValues().apply {
                    put("pet_id", petId)
                    put("uri", uri)
                }
                db.insert("pet_photos", null, values)
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun getPetPhotos(petId: Long): List<String> {
        val uris = mutableListOf<String>()
        val cursor = readableDatabase.query("pet_photos", arrayOf("uri"), "pet_id = ?", arrayOf(petId.toString()), null, null, null)
        cursor.use {
            while (it.moveToNext()) {
                uris.add(it.getString(it.getColumnIndexOrThrow("uri")))
            }
        }
        return uris
    }

    private fun ensureValidUserId(): Long {
        currentUserId()?.let { return it }

        val cursor = readableDatabase.query(TABLE_USERS, arrayOf(COLUMN_ID), null, null, null, null, "$COLUMN_ID ASC", "1")
        cursor.use {
            if (it.moveToFirst()) {
                return it.getLong(it.getColumnIndexOrThrow(COLUMN_ID))
            }
        }

        val uniqueEmail = "guest_${System.currentTimeMillis()}@petcare.app"
        val values = ContentValues().apply {
            put(COLUMN_NAME, "Pet Lover")
            put(COLUMN_EMAIL, uniqueEmail)
            put(COLUMN_PASSWORD_HASH, "GUEST_HASH")
            put(COLUMN_CREATED_AT, System.currentTimeMillis())
        }
        val newId = writableDatabase.insert(TABLE_USERS, null, values)
        if (newId != -1L) return newId

        val retryCursor = readableDatabase.query(TABLE_USERS, arrayOf(COLUMN_ID), null, null, null, null, "$COLUMN_ID ASC", "1")
        retryCursor.use {
            if (it.moveToFirst()) {
                return it.getLong(it.getColumnIndexOrThrow(COLUMN_ID))
            }
        }
        return 1L
    }

    private fun ensureLocationsTableExists(db: SQLiteDatabase) {
        try {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS pet_locations (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    user_id INTEGER DEFAULT 1,
                    name TEXT NOT NULL,
                    address TEXT NOT NULL,
                    category TEXT DEFAULT 'General',
                    latitude REAL DEFAULT 0.0,
                    longitude REAL DEFAULT 0.0
                )
                """.trimIndent()
            )
        } catch (_: Exception) {
        }
        try {
            db.execSQL("ALTER TABLE pet_locations ADD COLUMN latitude REAL DEFAULT 0.0")
        } catch (_: Exception) {
        }
        try {
            db.execSQL("ALTER TABLE pet_locations ADD COLUMN longitude REAL DEFAULT 0.0")
        } catch (_: Exception) {
        }
    }

    fun saveLocationAndGetId(name: String, address: String, category: String, latitude: Double = 0.0, longitude: Double = 0.0): Long {
        return try {
            val db = writableDatabase
            ensureLocationsTableExists(db)
            val userId = ensureValidUserId()
            val values = ContentValues().apply {
                put("user_id", userId)
                put("name", name.ifBlank { "Pet Location" })
                put("address", address.ifBlank { "Unspecified Address" })
                put("category", category.ifBlank { "General" })
                put("latitude", latitude)
                put("longitude", longitude)
            }
            db.insert("pet_locations", null, values)
        } catch (_: Exception) {
            -1L
        }
    }

    fun saveLocation(name: String, address: String, category: String, latitude: Double = 0.0, longitude: Double = 0.0): Boolean {
        return saveLocationAndGetId(name, address, category, latitude, longitude) != -1L
    }

    fun getLocations(): List<PetLocation> {
        val list = mutableListOf<PetLocation>()
        try {
            val db = readableDatabase
            ensureLocationsTableExists(db)
            val cursor = db.query(
                "pet_locations",
                null,
                null,
                null,
                null,
                null,
                "id DESC"
            )
            cursor.use {
                val latIndex = it.getColumnIndex("latitude")
                val lngIndex = it.getColumnIndex("longitude")
                while (it.moveToNext()) {
                    val lat = if (latIndex != -1 && !it.isNull(latIndex)) it.getDouble(latIndex) else 0.0
                    val lng = if (lngIndex != -1 && !it.isNull(lngIndex)) it.getDouble(lngIndex) else 0.0
                    list.add(PetLocation(
                        id = it.getLong(it.getColumnIndexOrThrow("id")),
                        userId = it.getLong(it.getColumnIndexOrThrow("user_id")),
                        name = it.getStringOrEmpty("name"),
                        address = it.getStringOrEmpty("address"),
                        category = it.getStringOrEmpty("category"),
                        latitude = lat,
                        longitude = lng
                    ))
                }
            }
        } catch (_: Exception) {
        }
        return list
    }

    fun updateLocationCoordinates(id: Long, latitude: Double, longitude: Double): Boolean {
        val values = ContentValues().apply {
            put("latitude", latitude)
            put("longitude", longitude)
        }
        return writableDatabase.update("pet_locations", values, "id = ?", arrayOf(id.toString())) > 0
    }

    fun deleteLocation(id: Long): Boolean {
        return try {
            writableDatabase.delete("pet_locations", "id = ?", arrayOf(id.toString())) > 0
        } catch (_: Exception) {
            false
        }
    }

    /** Every day each of the pet's routines was completed, newest first. Read-only history. */
    fun getCompletionHistory(petId: Long): List<CompletionEntry> {
        if (!canAccessPet(petId)) return emptyList()
        val entries = mutableListOf<CompletionEntry>()
        val cursor = readableDatabase.rawQuery(
            """
            SELECT t.id, t.description, t.category, c.completed_date
            FROM task_completions c
            INNER JOIN tasks t ON t.id = c.task_id
            WHERE t.pet_id = ?
            ORDER BY c.completed_date DESC, t.scheduled_time ASC
            """.trimIndent(),
            arrayOf(petId.toString())
        )
        cursor.use {
            while (it.moveToNext()) {
                entries.add(
                    CompletionEntry(
                        taskId = it.getLong(0),
                        description = it.getString(1).orEmpty(),
                        category = it.getString(2).orEmpty(),
                        date = it.getString(3).orEmpty()
                    )
                )
            }
        }
        return entries
    }

    /** Sets the pet's next vaccination due date ("dd/MM/yyyy"), e.g. after logging a vaccination. */
    fun updatePetVaccineDate(petId: Long, date: String): Boolean {
        if (!canAccessPet(petId)) return false
        val values = ContentValues().apply { put("vaccine_date", date) }
        return try {
            writableDatabase.update("pets", values, "id = ?", arrayOf(petId.toString())) > 0
        } catch (_: Exception) {
            false
        }
    }

    fun deleteHealthcareRecord(id: Long): Boolean {
        return try {
            writableDatabase.delete("healthcare_history", "id = ?", arrayOf(id.toString())) > 0
        } catch (_: Exception) {
            false
        }
    }

    fun saveHealthcareRecord(petId: Long, type: String, date: String, notes: String): Boolean {
        val values = ContentValues().apply {
            put("pet_id", petId)
            put("type", type)
            put("date", date)
            put("notes", notes)
        }
        return writableDatabase.insert("healthcare_history", null, values) != -1L
    }

    fun getHealthcareHistory(petId: Long): List<HealthcareRecord> {
        val list = mutableListOf<HealthcareRecord>()
        val cursor = readableDatabase.rawQuery(
            """
            SELECT h.*, p.name as pet_name
            FROM healthcare_history h
            INNER JOIN pets p ON h.pet_id = p.id
            WHERE h.pet_id = ?
            ORDER BY h.date DESC
            """.trimIndent(),
            arrayOf(petId.toString())
        )
        cursor.use {
            while (it.moveToNext()) {
                list.add(HealthcareRecord(
                    id = it.getLong(it.getColumnIndexOrThrow("id")),
                    petId = it.getLong(it.getColumnIndexOrThrow("pet_id")),
                    petName = it.getStringOrEmpty("pet_name"),
                    type = it.getStringOrEmpty("type"),
                    date = it.getStringOrEmpty("date"),
                    notes = it.getStringOrEmpty("notes")
                ))
            }
        }
        return list
    }

    fun getAllHealthcareHistory(): List<HealthcareRecord> {
        val list = mutableListOf<HealthcareRecord>()
        val userId = currentUserId() ?: return emptyList()
        
        // Joining with pets to ensure we only get history for pets owned by the current user
        val cursor = readableDatabase.rawQuery(
            """
            SELECT h.*, p.name as pet_name
            FROM healthcare_history h
            INNER JOIN pets p ON h.pet_id = p.id
            WHERE p.owner_id = ?
            ORDER BY h.date DESC
            """.trimIndent(),
            arrayOf(userId.toString())
        )
        
        cursor.use {
            while (it.moveToNext()) {
                list.add(HealthcareRecord(
                    id = it.getLong(it.getColumnIndexOrThrow("id")),
                    petId = it.getLong(it.getColumnIndexOrThrow("pet_id")),
                    petName = it.getStringOrEmpty("pet_name"),
                    type = it.getStringOrEmpty("type"),
                    date = it.getStringOrEmpty("date"),
                    notes = it.getStringOrEmpty("notes")
                ))
            }
        }
        return list
    }

    companion object {
        private const val DATABASE_NAME = "petcare_v3.db"
        private const val DATABASE_VERSION = 14
        private const val TABLE_USERS = "users"
        private const val COLUMN_ID = "id"
        private const val COLUMN_NAME = "name"
        private const val COLUMN_EMAIL = "email"
        private const val COLUMN_PASSWORD_HASH = "password_hash"
        private const val COLUMN_CREATED_AT = "created_at"

        /** Day key (yyyy-MM-dd) used to record per-day task completion. */
        fun dateKey(calendar: java.util.Calendar): String =
            java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(calendar.time)

        fun todayKey(): String = dateKey(java.util.Calendar.getInstance())
    }
}
