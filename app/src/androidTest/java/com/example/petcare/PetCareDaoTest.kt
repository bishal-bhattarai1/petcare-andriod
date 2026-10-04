package com.example.petcare

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.petcare.data.ExpenseEntity
import com.example.petcare.data.HealthRecordEntity
import com.example.petcare.data.PetCareDatabase
import com.example.petcare.data.PetEntity
import com.example.petcare.data.PlaceEntity
import com.example.petcare.data.TaskEntity
import com.example.petcare.data.UserEntity
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** DAO behaviour on an in-memory Room database: cascading pet delete and per-user filtering. */
@RunWith(AndroidJUnit4::class)
class PetCareDaoTest {

    private lateinit var db: PetCareDatabase
    private var alice = 0L
    private var bob = 0L

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext, PetCareDatabase::class.java
        ).build()
        alice = db.userDao().insert(UserEntity(name = "Alice", email = "alice@x.com", passwordHash = "h", createdAt = 0))
        bob = db.userDao().insert(UserEntity(name = "Bob", email = "bob@x.com", passwordHash = "h", createdAt = 0))
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun deletePetCascade_removesEverythingOfThatPetOnly() {
        val max = db.petDao().insert(PetEntity(name = "Max", ownerId = alice))
        val luna = db.petDao().insert(PetEntity(name = "Luna", ownerId = alice))
        val walk = db.taskDao().insert(TaskEntity(petId = max, description = "Walk"))
        val lunaFeed = db.taskDao().insert(TaskEntity(petId = luna, description = "Feed"))
        db.taskDao().insertCompletion(walk, "2026-10-04")
        db.taskDao().insertCompletion(lunaFeed, "2026-10-04")
        db.expenseDao().insert(ExpenseEntity(petId = max, category = "Food", description = "Bag", expenseDate = "01/10/2026", amount = 5.0, createdAt = 0))
        db.petPhotoDao().replacePhotos(max, listOf("content://a", "content://b"))
        db.healthRecordDao().insert(HealthRecordEntity(petId = max, type = "Vet", date = "01/10/2026"))

        assertTrue(db.petDao().deletePetCascade(max))

        // Max and all of Max's rows are gone...
        assertNull(db.petDao().petForUser(max, alice))
        assertTrue(db.taskDao().careTasks(max, alice, "2026-10-04", "2026-09-28", "2026-10-04").isEmpty())
        assertTrue(db.taskDao().completionHistory(max).isEmpty())
        assertTrue(db.expenseDao().expenses(max, alice, null).isEmpty())
        assertTrue(db.petPhotoDao().urisForPet(max).isEmpty())
        assertTrue(db.healthRecordDao().forPet(max, alice).isEmpty())
        // ...while Luna is untouched.
        assertEquals(1, db.taskDao().careTasks(luna, alice, "2026-10-04", "2026-09-28", "2026-10-04").size)
        assertEquals(1, db.taskDao().completionHistory(luna).size)
    }

    @Test
    fun queries_onlyReturnTheSignedInUsersData() {
        val alicePet = db.petDao().insert(PetEntity(name = "Max", ownerId = alice))
        val bobPet = db.petDao().insert(PetEntity(name = "Rex", ownerId = bob))
        db.taskDao().insert(TaskEntity(petId = alicePet, description = "Alice task"))
        db.taskDao().insert(TaskEntity(petId = bobPet, description = "Bob task"))
        val bobExpense = db.expenseDao().insert(ExpenseEntity(petId = bobPet, category = "Food", description = "Bob food", expenseDate = "01/10/2026", amount = 9.0, createdAt = 0))
        db.healthRecordDao().insert(HealthRecordEntity(petId = bobPet, type = "Vet", date = "01/10/2026"))
        val bobPlace = db.placeDao().insert(PlaceEntity(userId = bob, name = "Bob's vet", address = "x", category = "Vet"))

        assertEquals(listOf("Max"), db.petDao().petsForUser(alice).map { it.name })
        assertNull(db.petDao().petForUser(bobPet, alice))
        assertEquals(listOf("Alice task"), db.taskDao().careTasks(null, alice, "2026-10-04", "2026-09-28", "2026-10-04").map { it.description })
        assertTrue(db.expenseDao().expenses(null, alice, null).isEmpty())
        assertTrue(db.healthRecordDao().forUser(alice).isEmpty())
        assertTrue(db.placeDao().forUser(alice).isEmpty())
        assertNull(db.taskDao().ownedTaskId(1, bob)) // task 1 is Alice's

        // Alice can't delete Bob's rows.
        assertEquals(0, db.expenseDao().deleteForUser(bobExpense, alice))
        assertEquals(0, db.placeDao().deleteForUser(bobPlace, alice))
        assertEquals(1, db.expenseDao().expenses(null, bob, null).size)
        assertEquals(1, db.placeDao().forUser(bob).size)
    }

    @Test
    fun setCompletion_recordsDayAndTodayFlag() {
        val pet = db.petDao().insert(PetEntity(name = "Max", ownerId = alice))
        val task = db.taskDao().insert(TaskEntity(petId = pet, description = "Feed"))

        db.taskDao().setCompletion(task, completed = true, dateKey = "2026-10-04", isToday = true)
        val row = db.taskDao().careTasks(pet, alice, "2026-10-04", "2026-09-28", "2026-10-04").single()
        assertEquals(1, row.isCompleted)
        assertEquals("2026-10-04", row.weekCompletions)

        db.taskDao().setCompletion(task, completed = false, dateKey = "2026-10-04", isToday = true)
        assertEquals(0, db.taskDao().careTasks(pet, alice, "2026-10-04", "2026-09-28", "2026-10-04").single().isCompleted)
    }
}
