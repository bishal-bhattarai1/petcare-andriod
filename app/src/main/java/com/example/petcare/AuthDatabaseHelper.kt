package com.example.petcare

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.security.MessageDigest

class AuthDatabaseHelper(context: Context) :
    SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE $TABLE_USERS (
                $COLUMN_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COLUMN_NAME TEXT NOT NULL,
                $COLUMN_EMAIL TEXT NOT NULL UNIQUE,
                $COLUMN_PASSWORD_HASH TEXT NOT NULL,
                $COLUMN_CREATED_AT INTEGER NOT NULL
            )
            """.trimIndent()
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS $TABLE_USERS")
        onCreate(db)
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

    fun isValidLogin(email: String, password: String): Boolean {
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

    private fun String.normalizedEmail(): String = trim().lowercase()

    private fun String.hashForEmail(email: String): String {
        val saltedPassword = "${email.normalizedEmail()}:$this"
        val bytes = MessageDigest.getInstance("SHA-256").digest(saltedPassword.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }

    companion object {
        private const val DATABASE_NAME = "petcare.db"
        private const val DATABASE_VERSION = 1
        private const val TABLE_USERS = "users"
        private const val COLUMN_ID = "id"
        private const val COLUMN_NAME = "name"
        private const val COLUMN_EMAIL = "email"
        private const val COLUMN_PASSWORD_HASH = "password_hash"
        private const val COLUMN_CREATED_AT = "created_at"
    }
}
