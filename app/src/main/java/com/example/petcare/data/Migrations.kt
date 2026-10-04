package com.example.petcare.data

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** Schema migrations for [PetCareDatabase]. */
object Migrations {

    /**
     * One table to rebuild: Room's exact CREATE statement (copied from schemas/15.json), the
     * columns to fill, and the SELECT expression that reads each column from the old table.
     */
    private class TableRebuild(
        val table: String,
        val createSql: String,
        val columns: List<String>,
        val selectExprs: List<String> = columns,
        val indexSql: List<String> = emptyList()
    )

    /*
     * Version 14 (last SQLiteOpenHelper version) -> 15 (first Room version).
     *
     * Why rebuild every table instead of just bumping the version?
     *  - Tables reached v14 through many ALTER TABLE steps, so the column order and constraints
     *    differ between installs (e.g. on an upgraded phone tasks.is_completed comes before
     *    tasks.category). Room validates the schema strictly when it opens the database.
     *  - Rebuilding gives every install the identical schema Room expects, while copying all rows
     *    BY COLUMN NAME so no data moves into the wrong column.
     *
     * Data safety:
     *  - Every row is copied with its original id, so links between tables (pet_id, task_id, ...)
     *    and alarm ids used by reminders stay valid.
     *  - The AUTOINCREMENT counters (sqlite_sequence) are restored, so ids of deleted rows are
     *    never reused (a reused task id could fire a stale reminder).
     *  - NOT NULL columns use COALESCE so a NULL that slipped into an old table can never abort
     *    the migration (which would crash the app and block access to the data).
     *  - Room runs migrate() inside a transaction: if any statement fails, nothing is changed.
     *
     * New in v15: indexes on the columns used for per-user filtering and joins.
     */
    private val tables = listOf(
        TableRebuild(
            table = "users",
            createSql = "CREATE TABLE IF NOT EXISTS `users_new` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `email` TEXT NOT NULL, `password_hash` TEXT NOT NULL, `created_at` INTEGER NOT NULL)",
            columns = listOf("id", "name", "email", "password_hash", "created_at"),
            selectExprs = listOf("id", "COALESCE(name, '')", "email", "COALESCE(password_hash, '')", "COALESCE(created_at, 0)"),
            indexSql = listOf("CREATE UNIQUE INDEX IF NOT EXISTS `index_users_email` ON `users` (`email`)")
        ),
        TableRebuild(
            table = "pets",
            createSql = "CREATE TABLE IF NOT EXISTS `pets_new` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `species` TEXT, `breed` TEXT, `age` INTEGER, `weight` REAL, `diet` TEXT, `vaccine_date` TEXT, `reminder_enabled` INTEGER, `allergies` TEXT, `toys` TEXT, `notes` TEXT, `owner_id` INTEGER, `created_at` INTEGER)",
            columns = listOf("id", "name", "species", "breed", "age", "weight", "diet", "vaccine_date", "reminder_enabled", "allergies", "toys", "notes", "owner_id", "created_at"),
            selectExprs = listOf("id", "COALESCE(name, '')", "species", "breed", "age", "weight", "diet", "vaccine_date", "reminder_enabled", "allergies", "toys", "notes", "owner_id", "created_at"),
            indexSql = listOf("CREATE INDEX IF NOT EXISTS `index_pets_owner_id` ON `pets` (`owner_id`)")
        ),
        TableRebuild(
            table = "tasks",
            createSql = "CREATE TABLE IF NOT EXISTS `tasks_new` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `pet_id` INTEGER, `description` TEXT NOT NULL, `category` TEXT DEFAULT 'Feeding', `repeat_type` TEXT DEFAULT 'Daily', `week_days` TEXT, `scheduled_time` TEXT, `ends_on` TEXT, `delegate_enabled` INTEGER DEFAULT 0, `is_completed` INTEGER DEFAULT 0, `expense_amount` REAL DEFAULT 0.0, `completed_week_days` TEXT DEFAULT '', `reminder_enabled` INTEGER DEFAULT 0, `required_supplies` TEXT DEFAULT '', `task_notes` TEXT DEFAULT '', `linked_location_id` INTEGER DEFAULT -1)",
            columns = listOf("id", "pet_id", "description", "category", "repeat_type", "week_days", "scheduled_time", "ends_on", "delegate_enabled", "is_completed", "expense_amount", "completed_week_days", "reminder_enabled", "required_supplies", "task_notes", "linked_location_id"),
            selectExprs = listOf("id", "pet_id", "COALESCE(description, '')", "category", "repeat_type", "week_days", "scheduled_time", "ends_on", "delegate_enabled", "is_completed", "expense_amount", "completed_week_days", "reminder_enabled", "required_supplies", "task_notes", "linked_location_id"),
            indexSql = listOf("CREATE INDEX IF NOT EXISTS `index_tasks_pet_id` ON `tasks` (`pet_id`)")
        ),
        TableRebuild(
            table = "task_completions",
            createSql = "CREATE TABLE IF NOT EXISTS `task_completions_new` (`task_id` INTEGER NOT NULL, `completed_date` TEXT NOT NULL, PRIMARY KEY(`task_id`, `completed_date`))",
            columns = listOf("task_id", "completed_date")
        ),
        TableRebuild(
            table = "expenses",
            createSql = "CREATE TABLE IF NOT EXISTS `expenses_new` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `pet_id` INTEGER NOT NULL, `category` TEXT NOT NULL, `description` TEXT NOT NULL, `expense_date` TEXT NOT NULL, `amount` REAL DEFAULT 0.0, `created_at` INTEGER NOT NULL)",
            columns = listOf("id", "pet_id", "category", "description", "expense_date", "amount", "created_at"),
            selectExprs = listOf("id", "COALESCE(pet_id, -1)", "COALESCE(category, '')", "COALESCE(description, '')", "COALESCE(expense_date, '')", "amount", "COALESCE(created_at, 0)"),
            indexSql = listOf("CREATE INDEX IF NOT EXISTS `index_expenses_pet_id` ON `expenses` (`pet_id`)")
        ),
        TableRebuild(
            table = "pet_photos",
            createSql = "CREATE TABLE IF NOT EXISTS `pet_photos_new` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `pet_id` INTEGER NOT NULL, `uri` TEXT NOT NULL)",
            columns = listOf("id", "pet_id", "uri"),
            selectExprs = listOf("id", "COALESCE(pet_id, -1)", "COALESCE(uri, '')"),
            indexSql = listOf("CREATE INDEX IF NOT EXISTS `index_pet_photos_pet_id` ON `pet_photos` (`pet_id`)")
        ),
        TableRebuild(
            table = "healthcare_history",
            createSql = "CREATE TABLE IF NOT EXISTS `healthcare_history_new` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `pet_id` INTEGER NOT NULL, `type` TEXT NOT NULL, `date` TEXT NOT NULL, `notes` TEXT)",
            columns = listOf("id", "pet_id", "type", "date", "notes"),
            selectExprs = listOf("id", "COALESCE(pet_id, -1)", "COALESCE(type, '')", "COALESCE(date, '')", "notes"),
            indexSql = listOf("CREATE INDEX IF NOT EXISTS `index_healthcare_history_pet_id` ON `healthcare_history` (`pet_id`)")
        ),
        TableRebuild(
            table = "pet_locations",
            createSql = "CREATE TABLE IF NOT EXISTS `pet_locations_new` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `user_id` INTEGER NOT NULL, `name` TEXT NOT NULL, `address` TEXT NOT NULL, `category` TEXT NOT NULL, `latitude` REAL DEFAULT 0.0, `longitude` REAL DEFAULT 0.0)",
            columns = listOf("id", "user_id", "name", "address", "category", "latitude", "longitude"),
            // An older variant of this table allowed user_id NULL with DEFAULT 1; keep that meaning.
            selectExprs = listOf("id", "COALESCE(user_id, 1)", "COALESCE(name, '')", "COALESCE(address, '')", "COALESCE(category, 'General')", "latitude", "longitude"),
            indexSql = listOf("CREATE INDEX IF NOT EXISTS `index_pet_locations_user_id` ON `pet_locations` (`user_id`)")
        )
    )

    val MIGRATION_14_15 = object : Migration(14, 15) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // 1. Remember the AUTOINCREMENT counters; DROP TABLE removes a table's counter.
            db.execSQL("CREATE TEMP TABLE IF NOT EXISTS saved_sequence AS SELECT name, seq FROM sqlite_sequence")

            // 2. Rebuild each table: create new -> copy rows by column name -> drop old -> rename.
            for (t in tables) {
                db.execSQL(t.createSql)
                db.execSQL(
                    "INSERT INTO `${t.table}_new` (${t.columns.joinToString { "`$it`" }}) " +
                        "SELECT ${t.selectExprs.joinToString()} FROM `${t.table}`"
                )
                db.execSQL("DROP TABLE `${t.table}`")
                db.execSQL("ALTER TABLE `${t.table}_new` RENAME TO `${t.table}`")
                t.indexSql.forEach(db::execSQL)
            }

            // 3. Put back any counter that is higher than the max id the copy produced.
            db.execSQL(
                """
                UPDATE sqlite_sequence
                SET seq = (SELECT s.seq FROM saved_sequence s WHERE s.name = sqlite_sequence.name)
                WHERE seq < (SELECT s.seq FROM saved_sequence s WHERE s.name = sqlite_sequence.name)
                """.trimIndent()
            )
            // Tables that were empty after the copy have no counter row yet: recreate it.
            db.execSQL(
                """
                INSERT INTO sqlite_sequence (name, seq)
                SELECT s.name, s.seq FROM saved_sequence s
                WHERE s.name NOT IN (SELECT name FROM sqlite_sequence)
                  AND s.name IN ('users', 'pets', 'tasks', 'expenses', 'pet_photos', 'healthcare_history', 'pet_locations')
                """.trimIndent()
            )
            db.execSQL("DROP TABLE saved_sequence")
        }
    }
}
