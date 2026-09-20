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
    val avatarUri: String? = null
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
    val category: String
)

data class HealthcareRecord(
    val id: Long,
    val petId: Long,
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
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 3) {
            db.execSQL("DROP TABLE IF EXISTS pets")
            db.execSQL("DROP TABLE IF EXISTS tasks")
            createUsersTable(db)
            createPetsTable(db)
            createTasksTable(db)
            createExpensesTable(db)
            return
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
                FOREIGN KEY(user_id) REFERENCES $TABLE_USERS($COLUMN_ID) ON DELETE CASCADE
            )
            """.trimIndent()
        )
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
            currentUserId()?.let { put("owner_id", it) }
        }

        return try {
            writableDatabase.insert("pets", null, values)
        } catch (_: Exception) {
            -1L
        }
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
                    isCritical = pendingTasks > 0
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
        reminder: Boolean = false
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
        }

        return try {
            writableDatabase.insert("tasks", null, values)
        } catch (_: Exception) {
            -1L
        }
    }

    fun getCareTasks(petId: Long? = null): List<CareTask> {
        adoptOrphanPetsForCurrentUser()
        val tasks = mutableListOf<CareTask>()
        val clauses = mutableListOf<String>()
        val args = mutableListOf<String>()
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
                t.is_completed,
                t.week_days,
                t.completed_week_days,
                t.reminder_enabled
            FROM tasks t
            LEFT JOIN pets p ON p.id = t.pet_id
            $whereClause
            ORDER BY t.is_completed ASC, t.id DESC
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
                        reminderEnabled = it.getIntOrZero("reminder_enabled") == 1
                    )
                )
            }
        }

        return tasks
    }

    fun updateTaskCompletion(taskId: Long, completed: Boolean): Boolean {
        if (!canAccessTask(taskId)) return false

        val values = ContentValues().apply {
            put("is_completed", if (completed) 1 else 0)
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

    fun updateTaskDetails(taskId: Long, description: String, scheduledTime: String): Boolean {
        if (!canAccessTask(taskId)) return false

        val values = ContentValues().apply {
            put("description", description)
            put("scheduled_time", scheduledTime)
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

    fun getExpenses(petId: Long? = null): List<ExpenseTransaction> {
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
                FOREIGN KEY(owner_id) REFERENCES $TABLE_USERS($COLUMN_ID)
            )
            """.trimIndent()
        )
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

    fun saveLocation(name: String, address: String, category: String): Boolean {
        val userId = currentUserId() ?: return false
        val values = ContentValues().apply {
            put("user_id", userId)
            put("name", name)
            put("address", address)
            put("category", category)
        }
        return writableDatabase.insert("pet_locations", null, values) != -1L
    }

    fun getLocations(): List<PetLocation> {
        val userId = currentUserId() ?: return emptyList()
        val list = mutableListOf<PetLocation>()
        val cursor = readableDatabase.query("pet_locations", null, "user_id = ?", arrayOf(userId.toString()), null, null, "id DESC")
        cursor.use {
            while (it.moveToNext()) {
                list.add(PetLocation(
                    id = it.getLong(it.getColumnIndexOrThrow("id")),
                    userId = userId,
                    name = it.getStringOrEmpty("name"),
                    address = it.getStringOrEmpty("address"),
                    category = it.getStringOrEmpty("category")
                ))
            }
        }
        return list
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
        val cursor = readableDatabase.query("healthcare_history", null, "pet_id = ?", arrayOf(petId.toString()), null, null, "date DESC")
        cursor.use {
            while (it.moveToNext()) {
                list.add(HealthcareRecord(
                    id = it.getLong(it.getColumnIndexOrThrow("id")),
                    petId = petId,
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
            SELECT h.* 
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
        private const val DATABASE_VERSION = 9
        private const val TABLE_USERS = "users"
        private const val COLUMN_ID = "id"
        private const val COLUMN_NAME = "name"
        private const val COLUMN_EMAIL = "email"
        private const val COLUMN_PASSWORD_HASH = "password_hash"
        private const val COLUMN_CREATED_AT = "created_at"
    }
}
