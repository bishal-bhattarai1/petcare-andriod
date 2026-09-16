package com.example.petcare

import android.content.Intent
import android.os.Bundle
import android.util.Patterns
import android.widget.Button
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.petcare.data.PetCareDatabase
import com.example.petcare.data.SessionManager
import com.google.android.material.textfield.TextInputEditText

class LoginActivity : AppCompatActivity() {
    private lateinit var database: PetCareDatabase
    private lateinit var sessionManager: SessionManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_login)
        val paddingDp = (28 * resources.displayMetrics.density).toInt()
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main_login)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(paddingDp + systemBars.left, systemBars.top, paddingDp + systemBars.right, systemBars.bottom)
            insets
        }

        database = PetCareDatabase.getDatabase(this)
        sessionManager = SessionManager(this)

        val editTextEmail = findViewById<TextInputEditText>(R.id.editTextLoginEmail)
        val editTextPassword = findViewById<TextInputEditText>(R.id.editTextLoginPassword)
        val buttonLoginUser = findViewById<Button>(R.id.buttonLoginUser)
        val buttonForgotPasswordLink = findViewById<Button>(R.id.buttonForgotPasswordLink)

        buttonForgotPasswordLink.setOnClickListener {
            val intent = Intent(this, ForgotPasswordActivity::class.java)
            startActivity(intent)
        }

        buttonLoginUser.setOnClickListener {
            val email = editTextEmail.text.toString().trim().lowercase(java.util.Locale.ROOT)
            val password = editTextPassword.text.toString().trim()

            // 1. Validation for empty fields
            if (email.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "Please fill in all fields", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // 2. Validation for email format
            if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                Toast.makeText(this, "Please enter a valid email address", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // 3. Authenticate against database
            val user = database.userDao().getUserByEmail(email)
            if (user == null) {
                Toast.makeText(this, "Email address not found. Please sign up first.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val hashedPassword = com.example.petcare.utils.PasswordUtils.hashPassword(password)
            if (user.password == hashedPassword) {
                // Successful Login
                sessionManager.createSession(user.name, user.email)
                Toast.makeText(this, "Login Successful!", Toast.LENGTH_SHORT).show()

                val intent = Intent(this, DashboardActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                startActivity(intent)
                finish()
            } else {
                // Invalid credentials
                Toast.makeText(this, "Incorrect password. Please try again.", Toast.LENGTH_SHORT).show()
            }
        }
    }
}