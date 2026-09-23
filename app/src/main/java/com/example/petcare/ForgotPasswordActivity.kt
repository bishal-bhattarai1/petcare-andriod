package com.example.petcare

import android.os.Bundle
import android.text.method.HideReturnsTransformationMethod
import android.text.method.PasswordTransformationMethod
import android.view.View
import android.widget.EditText
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

import androidx.core.view.WindowInsetsControllerCompat

class ForgotPasswordActivity : AppCompatActivity() {
    private lateinit var database: AuthDatabaseHelper

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_forgot_password)
        database = AuthDatabaseHelper(this)

        updateStatusBarIcons()

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(android.R.id.content)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        val emailInput = findViewById<EditText>(R.id.recoveryEmailInput)
        val passwordInput = findViewById<EditText>(R.id.recoveryPasswordInput)
        val confirmPasswordInput = findViewById<EditText>(R.id.recoveryConfirmPasswordInput)
        val saveButton = findViewById<View>(R.id.recoverySaveButton)
        val backButton = findViewById<View>(R.id.backToLoginLink)

        // Password Toggles
        setupPasswordToggle(R.id.recoveryPasswordInput, R.id.recoveryPasswordEye)
        setupPasswordToggle(R.id.recoveryConfirmPasswordInput, R.id.recoveryConfirmPasswordEye)

        backButton.setOnClickListener { finish() }

        saveButton.setOnClickListener {
            val email = emailInput.text.toString().trim()
            val password = passwordInput.text.toString().trim()
            val confirmPassword = confirmPasswordInput.text.toString().trim()

            when {
                email.isBlank() || password.isBlank() || confirmPassword.isBlank() -> {
                    showMessage("Fill in all fields.")
                }
                password.length < 6 -> {
                    showMessage("Password must be at least 6 characters.")
                }
                password != confirmPassword -> {
                    showMessage("Passwords do not match.")
                }
                !database.emailExists(email) -> {
                    showMessage(getString(R.string.recovery_email_not_found))
                }
                database.updatePassword(email, password) -> {
                    showMessage(getString(R.string.recovery_success))
                    finish()
                }
                else -> showMessage("Failed to update password. Try again.")
            }
        }
    }

    private fun setupPasswordToggle(inputId: Int, eyeId: Int) {
        val input = findViewById<EditText>(inputId)
        val eye = findViewById<ImageView>(eyeId)
        var isVisible = false

        eye.setOnClickListener {
            isVisible = !isVisible
            if (isVisible) {
                input.transformationMethod = HideReturnsTransformationMethod.getInstance()
                eye.setImageResource(android.R.drawable.ic_menu_close_clear_cancel)
            } else {
                input.transformationMethod = PasswordTransformationMethod.getInstance()
                eye.setImageResource(android.R.drawable.ic_menu_view)
            }
            input.setSelection(input.text.length)
        }
    }

    private fun showMessage(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    private fun updateStatusBarIcons() {
        val isDarkMode = (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES
        WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightStatusBars = !isDarkMode
    }
}
