package com.example.petcare.data

import android.content.Context
import android.database.sqlite.SQLiteConstraintException
import androidx.annotation.WorkerThread
import com.example.petcare.CareTask
import com.example.petcare.CompletionEntry
import com.example.petcare.DateKeys
import com.example.petcare.ExpenseTransaction
import com.example.petcare.HealthcareRecord
import com.example.petcare.PasswordHasher
import com.example.petcare.PetDashboardModel
import com.example.petcare.PetLocation
import com.example.petcare.PetOption
import com.example.petcare.SessionManager

/*
 * Repository layer: the only code the screens talk to for data. Repositories
 *  - resolve the signed-in user and pass their id into every DAO query (per-user filtering),
 *  - check ownership before changing a pet / routine / record,
 *  - map Room rows to the UI models in Models.kt,
 *  - keep the business rules that used to live in AuthDatabaseHelper.
 *
 * Every public function is blocking and annotated @WorkerThread: call it from a coroutine on
 * Dispatchers.IO, a ViewModel, or a background executor — never from the main thread.
 */

/** Works out who is signed in and whether they may touch a given pet or routine. */
class UserScope(context: Context) {
    private val appContext = context.applicationContext
    private val db = PetCareDatabase.getInstance(appContext)

    /** users.id of the signed-in account, or null if nobody is signed in. */
    @WorkerThread
    fun currentUserId(): Long? {
        val email = SessionManager(appContext).getUserEmail().orEmpty().normalizedEmail()
        if (email.isBlank()) return null
        return db.userDao().idByEmail(email)
    }

    /** The signed-in user, else the first account, else a new guest account (old helper behaviour for places). */
    @WorkerThread
    fun ensureValidUserId(): Long {
        currentUserId()?.let { return it }
        db.userDao().firstUserId()?.let { return it }
        return try {
            db.userDao().insert(
                UserEntity(
                    name = "Pet Lover",
                    email = "guest_${System.currentTimeMillis()}@petcare.app",
                    passwordHash = AuthRepository.GUEST_PASSWORD_PLACEHOLDER,
                    createdAt = System.currentTimeMillis()
                )
            )
        } catch (_: Exception) {
            db.userDao().firstUserId() ?: 1L
        }
    }

    /** Pets saved before accounts existed belong to whoever signs in first. */
    @WorkerThread
    fun adoptOrphanPets() {
        val userId = currentUserId() ?: return
        runCatching { db.petDao().adoptOrphans(userId) }
    }

    @WorkerThread
    fun canAccessPet(petId: Long): Boolean {
        val userId = currentUserId() ?: return petId > 0
        return db.petDao().petForUser(petId, userId) != null
    }

    @WorkerThread
    fun canAccessTask(taskId: Long): Boolean {
        val userId = currentUserId() ?: return taskId > 0
        return db.taskDao().ownedTaskId(taskId, userId) != null
    }
}

internal fun String.normalizedEmail(): String = trim().lowercase()

/** Accounts and passwords (PBKDF2 via [PasswordHasher], see Task 1). */
class AuthRepository(context: Context) {
    private val users = PetCareDatabase.getInstance(context).userDao()

    @WorkerThread
    fun createUser(name: String, email: String, password: String): Boolean = insertUser(
        UserEntity(
            name = name.trim(),
            email = email.normalizedEmail(),
            passwordHash = passwordHasher.hash(password), // PBKDF2 with a random per-user salt
            createdAt = System.currentTimeMillis()
        )
    )

    /** Google sign-in: creates a local account without a password the first time; reuses an existing one. */
    @WorkerThread
    fun createSocialUser(name: String, email: String): Boolean {
        val normalized = email.normalizedEmail()
        if (emailExists(normalized)) return true
        return insertUser(
            UserEntity(
                name = name.trim(),
                email = normalized,
                passwordHash = SOCIAL_PASSWORD_PLACEHOLDER,
                createdAt = System.currentTimeMillis()
            )
        )
    }

    private fun insertUser(user: UserEntity): Boolean = try {
        users.insert(user)
        true
    } catch (_: SQLiteConstraintException) {
        false // email already registered
    } catch (_: Exception) {
        false
    }

    /** Password login: the user's name if [password] is correct, otherwise null. Runs PBKDF2. */
    @WorkerThread
    fun getUserName(email: String, password: String): String? =
        if (checkPassword(email, password)) getUserNameByEmail(email) else null

    @WorkerThread
    fun getUserNameByEmail(email: String): String? = users.findByEmail(email.normalizedEmail())?.name

    @WorkerThread
    fun emailExists(email: String): Boolean = users.idByEmail(email.normalizedEmail()) != null

    @WorkerThread
    fun updateUserName(email: String, newName: String): Boolean =
        runCatching { users.updateName(email.normalizedEmail(), newName.trim()) > 0 }.getOrDefault(false)

    /** True if [password] is the account's current password (used by Change Password). */
    @WorkerThread
    fun verifyPassword(email: String, password: String): Boolean = checkPassword(email, password)

    /** False for Google-only (and guest) accounts, which have no local password. */
    @WorkerThread
    fun hasLocalPassword(email: String): Boolean {
        val stored = users.findByEmail(email.normalizedEmail())?.passwordHash ?: return false
        return stored != SOCIAL_PASSWORD_PLACEHOLDER && stored != GUEST_PASSWORD_PLACEHOLDER
    }

    /** Stores a new PBKDF2 hash (fresh salt) for [password]. */
    @WorkerThread
    fun updatePassword(email: String, password: String): Boolean =
        runCatching { users.updatePasswordHash(email.normalizedEmail(), passwordHasher.hash(password)) > 0 }
            .getOrDefault(false)

    /**
     * Verifies [password]; when it is correct but stored in the old SHA-256 format (or with fewer
     * iterations), re-hashes it with PBKDF2. Unknown emails still cost one hash so login timing
     * doesn't reveal which emails are registered.
     */
    private fun checkPassword(email: String, password: String): Boolean {
        val stored = users.findByEmail(email.normalizedEmail())?.passwordHash
        if (stored == null) {
            passwordHasher.dummyVerify(password)
            return false
        }
        return when (passwordHasher.verify(password, stored, email)) {
            PasswordHasher.Result.Valid -> true
            PasswordHasher.Result.ValidNeedsUpgrade -> {
                updatePassword(email, password) // legacy account: replace the weak hash
                true
            }
            PasswordHasher.Result.Invalid -> false
        }
    }

    companion object {
        // Non-hash values stored in password_hash; PasswordHasher.verify() never accepts them.
        const val SOCIAL_PASSWORD_PLACEHOLDER = "SOCIAL_LOGIN_NO_PASSWORD"
        const val GUEST_PASSWORD_PLACEHOLDER = "GUEST_HASH"

        /** Shared, thread-safe PBKDF2 hasher. */
        private val passwordHasher = PasswordHasher()
    }
}

/** Pet profiles, photos and the dashboard summary. */
class PetRepository(context: Context) {
    private val db = PetCareDatabase.getInstance(context)
    private val scope = UserScope(context)

    @WorkerThread
    fun savePet(
        name: String, species: String, breed: String, age: Int, weight: Double, diet: String,
        vaccine: String, reminder: Boolean, allergies: String, toys: String, notes: String
    ): Long = try {
        db.petDao().insert(
            PetEntity(
                name = name, species = species, breed = breed, age = age, weight = weight, diet = diet,
                vaccineDate = vaccine, reminderEnabled = if (reminder) 1 else 0, allergies = allergies,
                toys = toys, notes = notes, ownerId = scope.currentUserId(), createdAt = System.currentTimeMillis()
            )
        )
    } catch (_: Exception) {
        -1L
    }

    /** Updates the editable fields; owner and creation time are kept. Only the owner can update. */
    @WorkerThread
    fun updatePet(
        petId: Long, name: String, species: String, breed: String, age: Int, weight: Double, diet: String,
        vaccine: String, reminder: Boolean, allergies: String, toys: String, notes: String
    ): Boolean {
        val pet = getPet(petId) ?: return false
        return runCatching {
            db.petDao().update(
                pet.copy(
                    name = name, species = species, breed = breed, age = age, weight = weight, diet = diet,
                    vaccineDate = vaccine, reminderEnabled = if (reminder) 1 else 0, allergies = allergies,
                    toys = toys, notes = notes
                )
            ) > 0
        }.getOrDefault(false)
    }

    /** The pet if it belongs to the signed-in user, else null. */
    @WorkerThread
    fun getPet(petId: Long): PetEntity? = db.petDao().petForUser(petId, scope.currentUserId())

    @WorkerThread
    fun getPetOptions(): List<PetOption> {
        scope.adoptOrphanPets()
        return db.petDao().petsForUser(scope.currentUserId()).map { PetOption(it.id, it.name.ifBlank { "Unnamed pet" }) }
    }

    /** Dashboard cards: each pet with today's routine progress, category chips and avatar. */
    @WorkerThread
    fun getAllPets(): List<PetDashboardModel> {
        scope.adoptOrphanPets()
        val petDao = db.petDao()
        return petDao.petsForUser(scope.currentUserId()).map { pet ->
            val counts = petDao.taskCounts(pet.id)
            val status = categoryStatus(petDao.categoryStatus(pet.id))
            val vaccineDate = pet.vaccineDate.orEmpty()
            val pending = counts.totalTasks - counts.completedTasks
            PetDashboardModel(
                id = pet.id,
                name = pet.name,
                breed = listOf(pet.breed.orEmpty(), pet.species.orEmpty())
                    .filter { it.isNotBlank() && it != "Unknown" }
                    .joinToString(" - ")
                    .ifBlank { "Pet profile" },
                progress = if (counts.totalTasks == 0) 0 else (counts.completedTasks * 100) / counts.totalTasks,
                totalTasks = counts.totalTasks,
                completedTasks = counts.completedTasks,
                statusAlert = when {
                    pending > 0 -> "$pending care ${if (pending == 1) "task" else "tasks"} pending"
                    counts.totalTasks > 0 -> "All care tasks done"
                    vaccineDate.isNotBlank() -> "Upcoming: $vaccineDate"
                    else -> null
                },
                isCritical = pending > 0,
                createdAt = pet.createdAt ?: 0L,
                isFed = status[0],
                isWalked = status[1],
                isMedsTaken = status[2],
                isGroomed = status[3],
                avatarUri = db.petPhotoDao().urisForPet(pet.id).firstOrNull()
            )
        }
    }

    /** [fed, walked, meds, groomed] — true if any routine of that kind is done today. */
    private fun categoryStatus(rows: List<CategoryDoneRow>): BooleanArray {
        val result = BooleanArray(4)
        rows.filter { it.completed == 1 }.forEach { row ->
            val cat = row.category.orEmpty().lowercase()
            when {
                cat.contains("feed") || cat.contains("food") -> result[0] = true
                cat.contains("walk") || cat.contains("exercise") || cat.contains("activity") -> result[1] = true
                cat.contains("med") || cat.contains("health") -> result[2] = true
                cat.contains("groom") -> result[3] = true
            }
        }
        return result
    }

    /** Deletes the pet with its routines, completions, expenses, photos and health records (one transaction). */
    @WorkerThread
    fun deletePet(petId: Long): Boolean {
        if (!scope.canAccessPet(petId)) return false
        return runCatching { db.petDao().deletePetCascade(petId) }.getOrDefault(false)
    }

    @WorkerThread
    fun savePetPhotos(petId: Long, uris: List<String>) {
        if (!scope.canAccessPet(petId)) return
        db.petPhotoDao().replacePhotos(petId, uris)
    }

    @WorkerThread
    fun getPetPhotos(petId: Long): List<String> =
        if (scope.canAccessPet(petId)) db.petPhotoDao().urisForPet(petId) else emptyList()

    /** Sets the pet's next vaccination due date ("dd/MM/yyyy"), e.g. after logging a vaccination. */
    @WorkerThread
    fun updatePetVaccineDate(petId: Long, date: String): Boolean {
        if (!scope.canAccessPet(petId)) return false
        return runCatching { db.petDao().updateVaccineDate(petId, date) > 0 }.getOrDefault(false)
    }
}

/** Vaccinations, vet visits and other health records. */
class HealthRepository(context: Context) {
    private val records = PetCareDatabase.getInstance(context).healthRecordDao()
    private val scope = UserScope(context)

    /** Inserts a record and returns its id (-1 on failure), so reminders can be attached to it. */
    @WorkerThread
    fun addHealthcareRecord(petId: Long, type: String, date: String, notes: String): Long {
        if (!scope.canAccessPet(petId)) return -1L
        return runCatching { records.insert(HealthRecordEntity(petId = petId, type = type, date = date, notes = notes)) }
            .getOrDefault(-1L)
    }

    @WorkerThread
    fun saveHealthcareRecord(petId: Long, type: String, date: String, notes: String): Boolean =
        addHealthcareRecord(petId, type, date, notes) != -1L

    @WorkerThread
    fun deleteHealthcareRecord(id: Long): Boolean =
        runCatching { records.deleteForUser(id, scope.currentUserId()) > 0 }.getOrDefault(false)

    @WorkerThread
    fun getHealthcareHistory(petId: Long): List<HealthcareRecord> =
        records.forPet(petId, scope.currentUserId()).map { it.toModel() }

    /** Every record of every pet the signed-in user owns (empty when signed out). */
    @WorkerThread
    fun getAllHealthcareHistory(): List<HealthcareRecord> {
        val userId = scope.currentUserId() ?: return emptyList()
        return records.forUser(userId).map { it.toModel() }
    }

    private fun HealthRecordRow.toModel() = HealthcareRecord(
        id = id, petId = petId, petName = petName.orEmpty(), type = type.orEmpty(), date = date.orEmpty(), notes = notes.orEmpty()
    )
}

/** Care routines and their per-day completions. */
class TaskRepository(context: Context) {
    private val tasks = PetCareDatabase.getInstance(context).taskDao()
    private val scope = UserScope(context)

    @WorkerThread
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
        scope.adoptOrphanPets()
        if (!scope.canAccessPet(petId)) return -1L
        return runCatching {
            tasks.insert(
                TaskEntity(
                    petId = petId, description = description, expenseAmount = expenseAmount, category = category,
                    repeatType = repeatType, weekDays = weekDays, scheduledTime = scheduledTime, endsOn = endsOn,
                    delegateEnabled = if (delegate) 1 else 0, reminderEnabled = if (reminder) 1 else 0,
                    requiredSupplies = supplies, taskNotes = notes, linkedLocationId = locationId
                )
            )
        }.getOrDefault(-1L)
    }

    /** The signed-in user's routines (optionally one pet's) with completion state for [dateKey]. */
    @WorkerThread
    fun getCareTasks(petId: Long? = null, dateKey: String = DateKeys.todayKey()): List<CareTask> {
        scope.adoptOrphanPets()
        runCatching { tasks.fixEmptyWeeklyTasks() }
        val (weekStart, weekEnd) = DateKeys.weekRange(dateKey)
        return tasks.careTasks(petId, scope.currentUserId(), dateKey, weekStart, weekEnd).map { row ->
            CareTask(
                id = row.id,
                petId = row.petId ?: 0L,
                petName = row.petName.orEmpty().ifBlank { "Pet" },
                description = row.description.orEmpty(),
                category = row.category.orEmpty().ifBlank { "Care" },
                repeatType = row.repeatType.orEmpty().ifBlank { "Daily" },
                scheduledTime = row.scheduledTime.orEmpty(),
                isCompleted = row.isCompleted == 1,
                weekDays = row.weekDays.orEmpty(),
                completedWeekDays = DateKeys.weekDayCodes(row.weekCompletions.orEmpty()),
                reminderEnabled = row.reminderEnabled == 1,
                requiredSupplies = row.requiredSupplies.orEmpty(),
                taskNotes = row.taskNotes.orEmpty(),
                linkedLocationId = row.linkedLocationId ?: -1L,
                locationName = row.locationName.orEmpty(),
                locationAddress = row.locationAddress.orEmpty()
            )
        }
    }

    @WorkerThread
    fun updateTaskCompletion(taskId: Long, completed: Boolean, dateKey: String = DateKeys.todayKey()): Boolean {
        if (!scope.canAccessTask(taskId)) return false
        return runCatching { tasks.setCompletion(taskId, completed, dateKey, dateKey == DateKeys.todayKey()); true }
            .getOrDefault(false)
    }

    @WorkerThread
    fun updateTaskDetails(taskId: Long, description: String, scheduledTime: String, supplies: String, notes: String): Boolean {
        if (!scope.canAccessTask(taskId)) return false
        return runCatching { tasks.updateDetails(taskId, description, scheduledTime, supplies, notes) > 0 }.getOrDefault(false)
    }

    /** Deletes a routine and its completion history (one transaction). */
    @WorkerThread
    fun deleteTask(taskId: Long): Boolean {
        if (!scope.canAccessTask(taskId)) return false
        return runCatching { tasks.deleteTaskCascade(taskId) }.getOrDefault(false)
    }

    /** Every day each of the pet's routines was completed, newest first. Read-only history. */
    @WorkerThread
    fun getCompletionHistory(petId: Long): List<CompletionEntry> {
        if (!scope.canAccessPet(petId)) return emptyList()
        return tasks.completionHistory(petId).map {
            CompletionEntry(it.id, it.description.orEmpty(), it.category.orEmpty(), it.completedDate)
        }
    }
}

/** Money spent on pets. */
class ExpenseRepository(context: Context) {
    private val expenses = PetCareDatabase.getInstance(context).expenseDao()
    private val scope = UserScope(context)

    @WorkerThread
    fun saveExpense(petId: Long, category: String, description: String, date: String, amount: Double): Boolean {
        scope.adoptOrphanPets()
        if (!scope.canAccessPet(petId)) return false
        return runCatching {
            expenses.insert(
                ExpenseEntity(
                    petId = petId, category = category, description = description, expenseDate = date,
                    amount = amount, createdAt = System.currentTimeMillis()
                )
            ) != -1L
        }.getOrDefault(false)
    }

    /** The signed-in user's expenses, optionally for one pet and/or matching a search [query]. */
    @WorkerThread
    fun getExpenses(petId: Long? = null, query: String? = null): List<ExpenseTransaction> {
        scope.adoptOrphanPets()
        return expenses.expenses(petId, scope.currentUserId(), query?.takeIf { it.isNotBlank() }).map {
            ExpenseTransaction(
                id = it.id, petId = it.petId, petName = it.petName.orEmpty(), category = it.category.orEmpty(),
                description = it.description.orEmpty(), date = it.expenseDate.orEmpty(), amount = it.amount ?: 0.0
            )
        }
    }

    @WorkerThread
    fun deleteExpense(expenseId: Long): Boolean =
        runCatching { expenses.deleteForUser(expenseId, scope.currentUserId()) > 0 }.getOrDefault(false)
}

/** Geotagged places (vet, groomer, park). */
class PlaceRepository(context: Context) {
    private val places = PetCareDatabase.getInstance(context).placeDao()
    private val scope = UserScope(context)

    @WorkerThread
    fun saveLocationAndGetId(name: String, address: String, category: String, latitude: Double = 0.0, longitude: Double = 0.0): Long =
        runCatching {
            places.insert(
                PlaceEntity(
                    userId = scope.ensureValidUserId(),
                    name = name.ifBlank { "Pet Location" },
                    address = address.ifBlank { "Unspecified Address" },
                    category = category.ifBlank { "General" },
                    latitude = latitude,
                    longitude = longitude
                )
            )
        }.getOrDefault(-1L)

    @WorkerThread
    fun saveLocation(name: String, address: String, category: String, latitude: Double = 0.0, longitude: Double = 0.0): Boolean =
        saveLocationAndGetId(name, address, category, latitude, longitude) != -1L

    /** Places saved by the signed-in user (all places when signed out, as before). */
    @WorkerThread
    fun getLocations(): List<PetLocation> =
        runCatching {
            places.forUser(scope.currentUserId()).map {
                PetLocation(it.id, it.userId, it.name, it.address, it.category, it.latitude ?: 0.0, it.longitude ?: 0.0)
            }
        }.getOrDefault(emptyList())

    @WorkerThread
    fun updateLocationCoordinates(id: Long, latitude: Double, longitude: Double): Boolean =
        runCatching { places.updateCoordinates(id, latitude, longitude, scope.currentUserId()) > 0 }.getOrDefault(false)

    @WorkerThread
    fun deleteLocation(id: Long): Boolean =
        runCatching { places.deleteForUser(id, scope.currentUserId()) > 0 }.getOrDefault(false)
}
