package com.example.petcare

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText

class PersonalInfoActivity : AppCompatActivity() {
    private lateinit var database: AuthDatabaseHelper
    private lateinit var sessionManager: SessionManager
    
    private lateinit var imageAvatar: ImageView
    private lateinit var editName: TextInputEditText
    private lateinit var editEmail: TextInputEditText
    private lateinit var editPhone: TextInputEditText

    private val pickMedia = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            sessionManager.setProfileImageUri(uri.toString())
            imageAvatar.setImageURI(uri)
            imageAvatar.imageTintList = null
            Toast.makeText(this, "Profile picture updated", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_personal_info)
        
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

        initViews()
        loadUserData()
        setupListeners()
    }

    private fun initViews() {
        imageAvatar = findViewById(R.id.imageAvatar)
        editName = findViewById(R.id.editName)
        editEmail = findViewById(R.id.editEmail)
        editPhone = findViewById(R.id.editPhone)
    }

    private fun loadUserData() {
        editName.setText(sessionManager.getUserName())
        editEmail.setText(sessionManager.getUserEmail())
        editPhone.setText(sessionManager.getDefaultDelegateContact())
        
        val imageUriString = sessionManager.getProfileImageUri()
        if (imageUriString != null) {
            imageAvatar.setImageURI(Uri.parse(imageUriString))
            imageAvatar.imageTintList = null
        }
    }

    private fun setupListeners() {
        findViewById<android.view.View>(R.id.layoutAvatarContainer).setOnClickListener {
            pickMedia.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }

        findViewById<MaterialButton>(R.id.buttonSave).setOnClickListener {
            saveChanges()
        }
    }

    private fun saveChanges() {
        val newName = editName.text.toString().trim()
        val newPhone = editPhone.text.toString().trim()
        val email = sessionManager.getUserEmail().orEmpty()

        if (newName.isEmpty()) {
            Toast.makeText(this, "Name is required", Toast.LENGTH_SHORT).show()
            return
        }

        val nameUpdated = database.updateUserName(email, newName)
        if (nameUpdated) {
            sessionManager.updateUserInfo(newName, email)
            sessionManager.setDefaultDelegateContact(newPhone)
            Toast.makeText(this, "Profile updated successfully", Toast.LENGTH_SHORT).show()
            // We don't finish() here as per user request "No need to redirect automatically"
            // But we can finish() to go back to Profile Activity which will refresh.
            // User said "Create one page where all the information wil display in Perosnal Information Page add one back icon also to go back."
            // So staying on page is fine, or finishing is also fine if it feels like "going back".
            // I'll stay on page but show success.
        } else {
            Toast.makeText(this, "Failed to update profile", Toast.LENGTH_SHORT).show()
        }
    }

    private fun updateStatusBarIcons() {
        val isDarkMode = (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES
        WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightStatusBars = !isDarkMode
    }
}
