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
import com.example.petcare.data.UserEntity
import com.google.android.material.textfield.TextInputEditText

class SignUpActivity : AppCompatActivity() {
    private lateinit var database: PetCareDatabase
    private lateinit var sessionManager: SessionManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_sign_up)
        val paddingDp = (28 * resources.displayMetrics.density).toInt()
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main_signup)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(paddingDp + systemBars.left, systemBars.top, paddingDp + systemBars.right, systemBars.bottom)
            insets
        }

        database = PetCareDatabase.getDatabase(this)
        sessionManager = SessionManager(this)

        val editTextName = findViewById<TextInputEditText>(R.id.editTextName)
        val editTextEmail = findViewById<TextInputEditText>(R.id.editTextEmail)
        val editTextPassword = findViewById<TextInputEditText>(R.id.editTextPassword)
        val editTextConfirmPassword = findViewById<TextInputEditText>(R.id.editTextConfirmPassword)
        val buttonRegister = findViewById<Button>(R.id.buttonRegister)

        buttonRegister.setOnClickListener {
            val name = editTextName.text.toString().trim()
            val email = editTextEmail.text.toString().trim().lowercase(java.util.Locale.ROOT)
            val password = editTextPassword.text.toString().trim()
            val confirmPassword = editTextConfirmPassword.text.toString().trim()

            // 1. Validation for empty fields
            if (name.isEmpty() || email.isEmpty() || password.isEmpty() || confirmPassword.isEmpty()) {
                Toast.makeText(this, "Please fill in all fields", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // 2. Validation for email format
            if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                Toast.makeText(this, "Please enter a valid email address", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // 3. Validation for password length
            if (password.length < 6) {
                Toast.makeText(this, "Password must be at least 6 characters long", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // 4. Validation for password match
            if (password != confirmPassword) {
                Toast.makeText(this, "Passwords do not match", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // 5. Check if user already exists
            val existingUser = database.userDao().getUserByEmail(email)
            if (existingUser != null) {
                Toast.makeText(this, "Email is already registered", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // 6. Save user to Room Database
            val hashedPassword = com.example.petcare.utils.PasswordUtils.hashPassword(password)
            val newUser = UserEntity(name, email, hashedPassword)
            database.userDao().insertUser(newUser)

            // 7. Create Session and navigate to Dashboard
            sessionManager.createSession(name, email)
            Toast.makeText(this, "Registration Successful!", Toast.LENGTH_SHORT).show()

            val intent = Intent(this, DashboardActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
        }
    }
}