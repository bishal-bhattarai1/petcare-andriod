package com.example.petcare

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText

class ChangePasswordActivity : AppCompatActivity() {
    private lateinit var database: AuthDatabaseHelper
    private lateinit var sessionManager: SessionManager
    
    private lateinit var editNew: TextInputEditText
    private lateinit var editConfirm: TextInputEditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_change_password)
        
        database = AuthDatabaseHelper(this)
        sessionManager = SessionManager(this)

        updateStatusBarIcons()
        
        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        toolbar.setNavigationOnClickListener { finish() }

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(android.R.id.content)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        editNew = findViewById(R.id.editNewPassword)
        editConfirm = findViewById(R.id.editConfirmPassword)

        findViewById<MaterialButton>(R.id.buttonUpdate).setOnClickListener {
            updatePassword()
        }
    }

    private fun updatePassword() {
        val newPass = editNew.text.toString()
        val confirmPass = editConfirm.text.toString()
        val email = sessionManager.getUserEmail().orEmpty()

        if (newPass.isEmpty() || confirmPass.isEmpty()) {
            Toast.makeText(this, "Please fill in all fields", Toast.LENGTH_SHORT).show()
            return
        }

        if (newPass != confirmPass) {
            Toast.makeText(this, "Passwords do not match", Toast.LENGTH_SHORT).show()
            return
        }
        
        if (newPass.length < 6) {
            Toast.makeText(this, "Password must be at least 6 characters", Toast.LENGTH_SHORT).show()
            return
        }

        if (database.updatePassword(email, newPass)) {
            Toast.makeText(this, "Password changed. Please login again.", Toast.LENGTH_LONG).show()
            
            // Logout and restart
            sessionManager.logout()
            startActivity(Intent(this, MainActivity::class.java))
            finishAffinity()
        } else {
            Toast.makeText(this, "Failed to update password", Toast.LENGTH_SHORT).show()
        }
    }

    private fun updateStatusBarIcons() {
        val isDarkMode = (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES
        WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightStatusBars = !isDarkMode
    }
}
