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

class AddTaskActivity : AppCompatActivity() {
    private lateinit var database: AuthDatabaseHelper

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_add_task)
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

        val descInput = findViewById<EditText>(R.id.editTextDesc)
        val expenseInput = findViewById<EditText>(R.id.editTextExpense)
        val saveButton = findViewById<Button>(R.id.buttonSaveTask)

        saveButton.setOnClickListener {
            val desc = descInput.text.toString().trim()
            if (desc.isEmpty()) {
                Toast.makeText(this, "Please describe the task", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            
            Toast.makeText(this, "Care routine saved!", Toast.LENGTH_SHORT).show()
            finish()
        }
    }
}
