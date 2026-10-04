package com.example.petcare

import android.database.sqlite.SQLiteDatabase
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.petcare.data.Migrations
import com.example.petcare.data.PetCareDatabase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Proves that upgrading from the last SQLiteOpenHelper database (version 14) to Room (version 15)
 * keeps every row of all 8 tables.
 *
 * Version 14 was never a Room schema, so there is no 14.json: the test builds the v14 file with the
 * exact SQL the old app produced, then lets [MigrationTestHelper] run MIGRATION_14_15 and validate the
 * result against the exported schemas/15.json (columns, types, nullability, keys, indices).
 */
@RunWith(AndroidJUnit4::class)
class MigrationTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    @get:Rule
    val helper = MigrationTestHelper(instrumentation, PetCareDatabase::class.java)

    /** Schema of a phone that was upgraded step by step (ALTER TABLE put columns in a different order). */
    private val upgradedInstallSchema = listOf(
        "CREATE TABLE users (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL, email TEXT NOT NULL UNIQUE, password_hash TEXT NOT NULL, created_at INTEGER NOT NULL)",
        "CREATE TABLE pets (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL, species TEXT, breed TEXT, age INTEGER, weight REAL, diet TEXT, vaccine_date TEXT, reminder_enabled INTEGER, allergies TEXT, toys TEXT, notes TEXT, owner_id INTEGER, created_at INTEGER, FOREIGN KEY(owner_id) REFERENCES users(id))",
        "CREATE TABLE tasks (id INTEGER PRIMARY KEY AUTOINCREMENT, pet_id INTEGER, description TEXT NOT NULL, is_completed INTEGER DEFAULT 0, expense_amount REAL DEFAULT 0.0, category TEXT DEFAULT 'Feeding', repeat_type TEXT DEFAULT 'Daily', week_days TEXT, scheduled_time TEXT, ends_on TEXT, delegate_enabled INTEGER DEFAULT 0, completed_week_days TEXT DEFAULT '', reminder_enabled INTEGER DEFAULT 0, required_supplies TEXT DEFAULT '', task_notes TEXT DEFAULT '', linked_location_id INTEGER DEFAULT -1, FOREIGN KEY(pet_id) REFERENCES pets(id))",
        "CREATE TABLE expenses (id INTEGER PRIMARY KEY AUTOINCREMENT, pet_id INTEGER NOT NULL, category TEXT NOT NULL, description TEXT NOT NULL, expense_date TEXT NOT NULL, amount REAL DEFAULT 0.0, created_at INTEGER NOT NULL, FOREIGN KEY(pet_id) REFERENCES pets(id))",
        "CREATE TABLE pet_photos (id INTEGER PRIMARY KEY AUTOINCREMENT, pet_id INTEGER NOT NULL, uri TEXT NOT NULL, FOREIGN KEY(pet_id) REFERENCES pets(id) ON DELETE CASCADE)",
        "CREATE TABLE pet_locations (id INTEGER PRIMARY KEY AUTOINCREMENT, user_id INTEGER NOT NULL, name TEXT NOT NULL, address TEXT NOT NULL, category TEXT NOT NULL, latitude REAL DEFAULT 0.0, longitude REAL DEFAULT 0.0, FOREIGN KEY(user_id) REFERENCES users(id) ON DELETE CASCADE)",
        "CREATE TABLE healthcare_history (id INTEGER PRIMARY KEY AUTOINCREMENT, pet_id INTEGER NOT NULL, type TEXT NOT NULL, date TEXT NOT NULL, notes TEXT, FOREIGN KEY(pet_id) REFERENCES pets(id) ON DELETE CASCADE)",
        "CREATE TABLE task_completions (task_id INTEGER NOT NULL, completed_date TEXT NOT NULL, PRIMARY KEY (task_id, completed_date), FOREIGN KEY(task_id) REFERENCES tasks(id))"
    )

    /** Schema of a fresh v14 install (old helper's onCreate) — tasks columns in declaration order. */
    private val freshInstallSchema = upgradedInstallSchema.map {
        if (it.startsWith("CREATE TABLE tasks")) {
            "CREATE TABLE tasks (id INTEGER PRIMARY KEY AUTOINCREMENT, pet_id INTEGER, description TEXT NOT NULL, category TEXT DEFAULT 'Feeding', repeat_type TEXT DEFAULT 'Daily', week_days TEXT, scheduled_time TEXT, ends_on TEXT, delegate_enabled INTEGER DEFAULT 0, is_completed INTEGER DEFAULT 0, expense_amount REAL DEFAULT 0.0, completed_week_days TEXT DEFAULT '', reminder_enabled INTEGER DEFAULT 0, required_supplies TEXT DEFAULT '', task_notes TEXT DEFAULT '', linked_location_id INTEGER DEFAULT -1, FOREIGN KEY(pet_id) REFERENCES pets(id))"
        } else it
    }

    @Test
    fun migrate14To15_upgradedInstall_keepsAllData() = migrateAndVerify(upgradedInstallSchema)

    @Test
    fun migrate14To15_freshInstall_keepsAllData() = migrateAndVerify(freshInstallSchema)

    private fun migrateAndVerify(schema: List<String>) {
        createVersion14Database(schema)

        // Runs MIGRATION_14_15 and validates the result against schemas/15.json; throws on any mismatch.
        val db = helper.runMigrationsAndValidate(TEST_DB, 15, true, Migrations.MIGRATION_14_15)

        // users: legacy SHA-256 hash and the Google placeholder are untouched.
        db.query("SELECT id, name, email, password_hash, created_at FROM users ORDER BY id").use {
            assertEquals(2, it.count)
            it.moveToFirst()
            assertEquals(1L, it.getLong(0)); assertEquals("Owner", it.getString(1)); assertEquals("owner@petcare.app", it.getString(2))
            assertEquals(LEGACY_HASH, it.getString(3)); assertEquals(1_700_000_000_000L, it.getLong(4))
            it.moveToNext()
            assertEquals(6L, it.getLong(0)); assertEquals("SOCIAL_LOGIN_NO_PASSWORD", it.getString(3))
        }

        // pets: every column, including nullable ones.
        db.query("SELECT id, name, species, breed, age, weight, diet, vaccine_date, reminder_enabled, allergies, toys, notes, owner_id, created_at FROM pets WHERE id = 2").use {
            assertTrue(it.moveToFirst())
            assertEquals("Max", it.getString(1)); assertEquals("Dog", it.getString(2)); assertEquals("Beagle", it.getString(3))
            assertEquals(4, it.getInt(4)); assertEquals(12.5, it.getDouble(5), 0.0); assertEquals("Kibble", it.getString(6))
            assertEquals("01/12/2026", it.getString(7)); assertEquals(1, it.getInt(8)); assertEquals("Chicken", it.getString(9))
            assertEquals("Ball", it.getString(10)); assertEquals("Friendly", it.getString(11)); assertEquals(1L, it.getLong(12))
            assertEquals(1_700_000_100_000L, it.getLong(13))
        }
        assertEquals(2, count(db, "pets"))

        // tasks: values land in the right columns even though the old column order differed.
        db.query("SELECT pet_id, description, category, repeat_type, week_days, scheduled_time, ends_on, delegate_enabled, is_completed, expense_amount, completed_week_days, reminder_enabled, required_supplies, task_notes, linked_location_id FROM tasks WHERE id = 17").use {
            assertTrue(it.moveToFirst())
            assertEquals(2L, it.getLong(0)); assertEquals("Morning walk", it.getString(1)); assertEquals("Exercise", it.getString(2))
            assertEquals("Weekly", it.getString(3)); assertEquals("Mon,Wed", it.getString(4)); assertEquals("07:30 AM", it.getString(5))
            assertEquals("31/12/2026", it.getString(6)); assertEquals(1, it.getInt(7)); assertEquals(1, it.getInt(8))
            assertEquals(3.5, it.getDouble(9), 0.0); assertEquals("Mon", it.getString(10)); assertEquals(1, it.getInt(11))
            assertEquals("Leash", it.getString(12)); assertEquals("Avoid the main road", it.getString(13)); assertEquals(9L, it.getLong(14))
        }
        // A routine without a pet (pet_id NULL) is kept as it was.
        db.query("SELECT pet_id FROM tasks WHERE id = 1").use { assertTrue(it.moveToFirst()); assertTrue(it.isNull(0)) }
        assertEquals(2, count(db, "tasks"))

        assertEquals(2, count(db, "task_completions"))
        db.query("SELECT completed_date FROM task_completions WHERE task_id = 17 ORDER BY completed_date").use {
            it.moveToFirst(); assertEquals("2026-10-01", it.getString(0))
        }

        // expenses: including the orphan whose pet no longer exists (kept, not deleted).
        assertEquals(2, count(db, "expenses"))
        db.query("SELECT pet_id, category, description, expense_date, amount, created_at FROM expenses WHERE id = 3").use {
            assertTrue(it.moveToFirst())
            assertEquals(1L, it.getLong(0)); assertEquals("Vet", it.getString(1)); assertEquals("Check-up", it.getString(2))
            assertEquals("02/10/2026", it.getString(3)); assertEquals(45.0, it.getDouble(4), 0.0); assertEquals(1_700_000_200_000L, it.getLong(5))
        }

        db.query("SELECT pet_id, uri FROM pet_photos WHERE id = 4").use {
            assertTrue(it.moveToFirst()); assertEquals(2L, it.getLong(0)); assertEquals("content://photos/max.jpg", it.getString(1))
        }
        db.query("SELECT pet_id, type, date, notes FROM healthcare_history WHERE id = 15").use {
            assertTrue(it.moveToFirst())
            assertEquals(2L, it.getLong(0)); assertEquals("Vaccination", it.getString(1)); assertEquals("15/10/2026", it.getString(2))
            assertNull(it.getString(3))
        }
        db.query("SELECT user_id, name, address, category, latitude, longitude FROM pet_locations WHERE id = 9").use {
            assertTrue(it.moveToFirst())
            assertEquals(6L, it.getLong(0)); assertEquals("City Vet", it.getString(1)); assertEquals("1 Main St", it.getString(2))
            assertEquals("Vet", it.getString(3)); assertEquals(27.7, it.getDouble(4), 0.0); assertEquals(85.3, it.getDouble(5), 0.0)
        }

        // AUTOINCREMENT counters survive, so ids of deleted rows (used as alarm ids) are never reused.
        assertEquals(33L, sequence(db, "tasks"))
        assertEquals(8L, sequence(db, "expenses"))
        assertEquals(15L, sequence(db, "healthcare_history"))
        db.execSQL("INSERT INTO tasks (pet_id, description) VALUES (2, 'New routine')")
        db.query("SELECT MAX(id) FROM tasks").use { it.moveToFirst(); assertEquals(34L, it.getLong(0)) }

        // Duplicate emails are still rejected (unique index replaced the old UNIQUE constraint).
        val duplicateRejected = runCatching {
            db.execSQL("INSERT INTO users (name, email, password_hash, created_at) VALUES ('X', 'owner@petcare.app', 'h', 0)")
        }.isFailure
        assertTrue(duplicateRejected)
        db.close()
    }

    /** Writes a version-14 database file exactly as the old SQLiteOpenHelper app left it. */
    private fun createVersion14Database(schema: List<String>) {
        val file = instrumentation.targetContext.getDatabasePath(TEST_DB)
        file.parentFile?.mkdirs()
        file.delete()
        SQLiteDatabase.openOrCreateDatabase(file, null).use { db ->
            schema.forEach(db::execSQL)
            db.execSQL("INSERT INTO users VALUES (1, 'Owner', 'owner@petcare.app', '$LEGACY_HASH', 1700000000000)")
            db.execSQL("INSERT INTO users VALUES (6, 'Google User', 'g@petcare.app', 'SOCIAL_LOGIN_NO_PASSWORD', 1700000000001)")
            db.execSQL("INSERT INTO pets (id, name, species, breed, age, weight, diet, vaccine_date, reminder_enabled, allergies, toys, notes, owner_id, created_at) VALUES (2, 'Max', 'Dog', 'Beagle', 4, 12.5, 'Kibble', '01/12/2026', 1, 'Chicken', 'Ball', 'Friendly', 1, 1700000100000)")
            db.execSQL("INSERT INTO pets (id, name, owner_id) VALUES (3, 'Luna', 1)")
            db.execSQL("INSERT INTO tasks (id, pet_id, description) VALUES (1, NULL, 'Orphan routine')")
            db.execSQL(
                "INSERT INTO tasks (id, pet_id, description, category, repeat_type, week_days, scheduled_time, ends_on, delegate_enabled, is_completed, expense_amount, completed_week_days, reminder_enabled, required_supplies, task_notes, linked_location_id) " +
                    "VALUES (17, 2, 'Morning walk', 'Exercise', 'Weekly', 'Mon,Wed', '07:30 AM', '31/12/2026', 1, 1, 3.5, 'Mon', 1, 'Leash', 'Avoid the main road', 9)"
            )
            db.execSQL("INSERT INTO task_completions VALUES (17, '2026-10-01')")
            db.execSQL("INSERT INTO task_completions VALUES (17, '2026-10-03')")
            db.execSQL("INSERT INTO expenses VALUES (2, 2, 'Food', 'Kibble bag', '01/10/2026', 20.0, 1700000150000)")
            // Orphan: pet 1 was deleted long ago. Old app never enforced foreign keys.
            db.execSQL("INSERT INTO expenses VALUES (3, 1, 'Vet', 'Check-up', '02/10/2026', 45.0, 1700000200000)")
            db.execSQL("INSERT INTO pet_photos VALUES (4, 2, 'content://photos/max.jpg')")
            db.execSQL("INSERT INTO healthcare_history VALUES (15, 2, 'Vaccination', '15/10/2026', NULL)")
            db.execSQL("INSERT INTO pet_locations VALUES (9, 6, 'City Vet', '1 Main St', 'Vet', 27.7, 85.3)")
            // Rows were deleted in the past, so the counters are ahead of the max ids.
            db.execSQL("UPDATE sqlite_sequence SET seq = 33 WHERE name = 'tasks'")
            db.execSQL("UPDATE sqlite_sequence SET seq = 8 WHERE name = 'expenses'")
            db.version = 14
        }
    }

    private fun count(db: SupportSQLiteDatabase, table: String): Int =
        db.query("SELECT COUNT(*) FROM $table").use { it.moveToFirst(); it.getInt(0) }

    private fun sequence(db: SupportSQLiteDatabase, table: String): Long =
        db.query("SELECT seq FROM sqlite_sequence WHERE name = '$table'").use { it.moveToFirst(); it.getLong(0) }

    private companion object {
        const val TEST_DB = "migration-test.db"
        val LEGACY_HASH = "a".repeat(64)
    }
}
