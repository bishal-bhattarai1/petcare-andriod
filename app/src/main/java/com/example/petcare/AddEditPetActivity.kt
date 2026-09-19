package com.example.petcare

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

import androidx.core.view.WindowInsetsControllerCompat

class AddEditPetActivity : AppCompatActivity() {
    private lateinit var database: AuthDatabaseHelper

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_add_edit_pet)
        database = AuthDatabaseHelper(this)

        // Ensure status bar icons are dark on light background
        WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightStatusBars = true

        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        toolbar.setNavigationOnClickListener { finish() }

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(android.R.id.content)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        val nameInput = findViewById<EditText>(R.id.editTextName)
        val breedInput = findViewById<EditText>(R.id.editTextBreed)
        val ageInput = findViewById<EditText>(R.id.editTextAge)
        val saveButton = findViewById<Button>(R.id.buttonSave)

        saveButton.setOnClickListener {
            val name = nameInput.text.toString().trim()
            if (name.isEmpty()) {
                Toast.makeText(this, "Please enter a pet name", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            
            // Logic to save pet would go here
            Toast.makeText(this, "$name added!", Toast.LENGTH_SHORT).show()
            finish()
        }
    }
}
