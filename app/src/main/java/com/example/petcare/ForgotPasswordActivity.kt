package com.example.petcare

import android.os.Bundle
import android.util.Patterns
import android.widget.Button
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.petcare.data.PetCareDatabase
import com.example.petcare.utils.PasswordUtils
import com.google.android.material.textfield.TextInputEditText

class ForgotPasswordActivity : AppCompatActivity() {
    private lateinit var database: PetCareDatabase

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_forgot_password)
        val paddingDp = (28 * resources.displayMetrics.density).toInt()
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main_forgot_password)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(paddingDp + systemBars.left, systemBars.top, paddingDp + systemBars.right, systemBars.bottom)
            insets
        }

        database = PetCareDatabase.getDatabase(this)

        val editTextResetEmail = findViewById<TextInputEditText>(R.id.editTextResetEmail)
        val editTextResetPassword = findViewById<TextInputEditText>(R.id.editTextResetPassword)
        val editTextResetConfirmPassword = findViewById<TextInputEditText>(R.id.editTextResetConfirmPassword)
        val buttonSaveResetPassword = findViewById<Button>(R.id.buttonSaveResetPassword)

        buttonSaveResetPassword.setOnClickListener {
            val email = editTextResetEmail.text.toString().trim().lowercase(java.util.Locale.ROOT)
            val newPassword = editTextResetPassword.text.toString().trim()
            val confirmPassword = editTextResetConfirmPassword.text.toString().trim()

            if (email.isEmpty() || newPassword.isEmpty() || confirmPassword.isEmpty()) {
                Toast.makeText(this, "Please fill in all fields", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                Toast.makeText(this, "Please enter a valid email address", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (newPassword.length < 6) {
                Toast.makeText(this, "Password must be at least 6 characters long", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (newPassword != confirmPassword) {
                Toast.makeText(this, "Passwords do not match", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Verify if user account exists
            val user = database.userDao().getUserByEmail(email)
            if (user == null) {
                Toast.makeText(this, "Email address not found.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Encrypt and save updated password
            val hashed = PasswordUtils.hashPassword(newPassword)
            database.userDao().updatePassword(email, hashed)

            // Double check persistence
            val updatedUser = database.userDao().getUserByEmail(email)
            if (updatedUser != null && updatedUser.password == hashed) {
                Toast.makeText(this, "Password reset successful! Please log in.", Toast.LENGTH_LONG).show()
                finish()
            } else {
                Toast.makeText(this, "Failed to update database. Please try again.", Toast.LENGTH_SHORT).show()
            }
        }
    }
}