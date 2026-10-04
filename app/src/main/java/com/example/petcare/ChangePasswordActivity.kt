package com.example.petcare

import com.example.petcare.data.AuthRepository
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ChangePasswordActivity : AppCompatActivity() {
    private lateinit var auth: AuthRepository
    private lateinit var sessionManager: SessionManager
    
    private lateinit var editCurrent: TextInputEditText
    private lateinit var editNew: TextInputEditText
    private lateinit var editConfirm: TextInputEditText
    private lateinit var buttonUpdate: MaterialButton

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_change_password)
        
        auth = AuthRepository(this)
        sessionManager = SessionManager(this)

        updateStatusBarIcons()
        
        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        toolbar.setNavigationOnClickListener { finish() }

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(android.R.id.content)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        editCurrent = findViewById(R.id.editCurrentPassword)
        editNew = findViewById(R.id.editNewPassword)
        editConfirm = findViewById(R.id.editConfirmPassword)
        buttonUpdate = findViewById(R.id.buttonUpdate)

        buttonUpdate.setOnClickListener {
            updatePassword()
        }
    }

    private fun updatePassword() {
        // Trimmed like the login, sign-up and reset forms so the same password works everywhere.
        val currentPass = editCurrent.text.toString().trim()
        val newPass = editNew.text.toString().trim()
        val confirmPass = editConfirm.text.toString().trim()
        val email = sessionManager.getUserEmail().orEmpty()

        if (currentPass.isEmpty() || newPass.isEmpty() || confirmPass.isEmpty()) {
            Toast.makeText(this, "Please fill in all fields", Toast.LENGTH_SHORT).show()
            return
        }

        if (newPass != confirmPass) {
            Toast.makeText(this, "Passwords do not match", Toast.LENGTH_SHORT).show()
            return
        }

        if (newPass.length < PasswordHasher.MIN_PASSWORD_LENGTH) {
            Toast.makeText(this, "Password must be at least ${PasswordHasher.MIN_PASSWORD_LENGTH} characters", Toast.LENGTH_SHORT).show()
            return
        }

        if (newPass == currentPass) {
            Toast.makeText(this, "New password must be different from the current one", Toast.LENGTH_SHORT).show()
            return
        }

        buttonUpdate.isEnabled = false // block double taps while PBKDF2 runs
        lifecycleScope.launch {
            try {
                // Verify the current password, then store the new hash — both off the main thread.
                val outcome = withContext(Dispatchers.IO) {
                    when {
                        !auth.hasLocalPassword(email) -> ChangeOutcome.NO_LOCAL_PASSWORD
                        !auth.verifyPassword(email, currentPass) -> ChangeOutcome.WRONG_CURRENT
                        auth.updatePassword(email, newPass) -> ChangeOutcome.CHANGED
                        else -> ChangeOutcome.FAILED
                    }
                }
                handleOutcome(outcome)
            } finally {
                buttonUpdate.isEnabled = true
            }
        }
    }

    private fun handleOutcome(outcome: ChangeOutcome) {
        when (outcome) {
            ChangeOutcome.NO_LOCAL_PASSWORD -> Toast.makeText(
                this, "This account signs in with Google and has no password to change", Toast.LENGTH_LONG
            ).show()
            ChangeOutcome.WRONG_CURRENT -> {
                editCurrent.text?.clear()
                Toast.makeText(this, "Current password is incorrect", Toast.LENGTH_SHORT).show()
            }
            ChangeOutcome.CHANGED -> {
                Toast.makeText(this, "Password changed. Please login again.", Toast.LENGTH_LONG).show()

                // Logout and restart
                sessionManager.logout()
                startActivity(Intent(this, MainActivity::class.java))
                finishAffinity()
            }
            ChangeOutcome.FAILED -> Toast.makeText(this, "Failed to update password", Toast.LENGTH_SHORT).show()
        }
    }

    private enum class ChangeOutcome { NO_LOCAL_PASSWORD, WRONG_CURRENT, CHANGED, FAILED }

    private fun updateStatusBarIcons() {
        val isDarkMode = (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES
        WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightStatusBars = !isDarkMode
    }
}
