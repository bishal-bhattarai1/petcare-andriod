package com.example.petcare

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import android.widget.TextView
import android.widget.EditText
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

class MainActivity : AppCompatActivity() {
    private lateinit var database: AuthDatabaseHelper
    private lateinit var loginPanel: View
    private lateinit var signUpPanel: View

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        database = AuthDatabaseHelper(this)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        loginPanel = findViewById(R.id.loginPanel)
        signUpPanel = findViewById(R.id.signUpPanel)

        // Switch to Sign Up
        findViewById<View>(R.id.showSignUpButton).setOnClickListener { showSignUp() }
        
        // Switch to Login
        findViewById<View>(R.id.showLoginButton).setOnClickListener { showLogin() }

        // Action Buttons
        findViewById<View>(R.id.loginButton).setOnClickListener { login() }
        findViewById<View>(R.id.signUpButton).setOnClickListener { signUp() }
        
        // Social Placeholder
        findViewById<View>(R.id.buttonGoogleSignIn)?.setOnClickListener {
            Toast.makeText(this, "Google Sign-In Placeholder", Toast.LENGTH_SHORT).show()
        }

        // Forgot Password
        findViewById<View>(R.id.forgotPasswordLink)?.setOnClickListener {
            Toast.makeText(this, "Forgot Password functionality coming soon.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun login() {
        val email = textOf(R.id.loginEmailInput)
        val password = textOf(R.id.loginPasswordInput)

        when {
            email.isBlank() || password.isBlank() -> showMessage("Enter email and password.")
            database.isValidLogin(email, password) -> {
                val intent = Intent(this, DashboardActivity::class.java)
                startActivity(intent)
                finish()
            }
            else -> showMessage("Invalid email or password.")
        }
    }

    private fun signUp() {
        val name = textOf(R.id.signUpNameInput)
        val email = textOf(R.id.signUpEmailInput)
        val password = textOf(R.id.signUpPasswordInput)
        val confirmPassword = textOf(R.id.signUpConfirmPasswordInput)

        when {
            name.isBlank() || email.isBlank() || password.isBlank() || confirmPassword.isBlank() -> {
                showMessage("Complete all required fields.")
            }
            !android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches() -> {
                showMessage("Enter a valid email address.")
            }
            password.length < 6 -> {
                showMessage("Password must be at least 6 characters.")
            }
            password != confirmPassword -> {
                showMessage("Passwords do not match.")
            }
            database.emailExists(email) -> {
                showMessage("An account already exists for this email.")
            }
            database.createUser(name, email, password) -> {
                showMessage("Account created. You can login now.")
                clearSignUpFields()
                showLogin()
            }
            else -> showMessage("Could not create account.")
        }
    }

    private fun showLogin() {
        loginPanel.visibility = View.VISIBLE
        signUpPanel.visibility = View.GONE
    }

    private fun showSignUp() {
        loginPanel.visibility = View.GONE
        signUpPanel.visibility = View.VISIBLE
    }

    private fun textOf(inputId: Int): String =
        findViewById<EditText>(inputId).text?.toString()?.trim().orEmpty()

    private fun clearSignUpFields() {
        findViewById<EditText>(R.id.signUpNameInput).text?.clear()
        findViewById<EditText>(R.id.signUpEmailInput).text?.clear()
        findViewById<EditText>(R.id.signUpPasswordInput).text?.clear()
        findViewById<EditText>(R.id.signUpConfirmPasswordInput).text?.clear()
    }

    private fun showMessage(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
}
