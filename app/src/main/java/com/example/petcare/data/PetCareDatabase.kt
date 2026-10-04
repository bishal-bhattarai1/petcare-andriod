package com.example.petcare.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.petcare.DateKeys

/**
 * The app's Room database. It opens the SAME file the old SQLiteOpenHelper used ("petcare_v3.db"),
 * so on upgrade Room finds version 14 and runs [Migrations.MIGRATION_14_15] to keep all data.
 *
 * exportSchema = true writes app/schemas/<version>.json, which MigrationTest uses to validate
 * that the migrated database is exactly what these entities expect.
 */
@Database(
    entities = [
        UserEntity::class,
        PetEntity::class,
        TaskEntity::class,
        TaskCompletionEntity::class,
        ExpenseEntity::class,
        PetPhotoEntity::class,
        HealthRecordEntity::class,
        PlaceEntity::class
    ],
    version = PetCareDatabase.VERSION,
    exportSchema = true
)
abstract class PetCareDatabase : RoomDatabase() {
    abstract fun userDao(): UserDao
    abstract fun petDao(): PetDao
    abstract fun petPhotoDao(): PetPhotoDao
    abstract fun taskDao(): TaskDao
    abstract fun expenseDao(): ExpenseDao
    abstract fun healthRecordDao(): HealthRecordDao
    abstract fun placeDao(): PlaceDao

    companion object {
        /** File name kept from the SQLiteOpenHelper era so existing installs are upgraded in place. */
        const val DATABASE_NAME = "petcare_v3.db"

        /** 14 was the last SQLiteOpenHelper version; 15 is the first Room version. */
        const val VERSION = 15

        @Volatile
        private var instance: PetCareDatabase? = null

        /** Process-wide singleton (Room databases are expensive to open and are thread-safe). */
        fun getInstance(context: Context): PetCareDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(context.applicationContext, PetCareDatabase::class.java, DATABASE_NAME)
                    .addMigrations(Migrations.MIGRATION_14_15)
                    .addCallback(SyncCompletionFlagsCallback)
                    .build()
                    .also { instance = it }
            }
    }

    /**
     * Same job as the old helper's onOpen(): the legacy tasks.is_completed column means
     * "completed today", so it is recomputed from task_completions each time the database opens
     * (a new day therefore starts with every routine unticked).
     */
    private object SyncCompletionFlagsCallback : Callback() {
        override fun onOpen(db: SupportSQLiteDatabase) {
            if (db.isReadOnly) return
            try {
                db.execSQL(
                    """
                    UPDATE tasks SET is_completed = CASE WHEN EXISTS (
                        SELECT 1 FROM task_completions c WHERE c.task_id = tasks.id AND c.completed_date = ?
                    ) THEN 1 ELSE 0 END
                    """.trimIndent(),
                    arrayOf<Any>(DateKeys.todayKey())
                )
            } catch (_: Exception) {
                // Never block opening the database over a cosmetic flag.
            }
        }
    }
}
