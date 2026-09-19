package com.example.petcare

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

import androidx.core.view.WindowInsetsControllerCompat

class DashboardActivity : AppCompatActivity() {
    private lateinit var database: AuthDatabaseHelper

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_dashboard)
        database = AuthDatabaseHelper(this)

        // Ensure status bar icons are dark on light background
        WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightStatusBars = true

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main_dashboard)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // Setup Greeting
        findViewById<TextView>(R.id.textViewWelcome).text = "Hey, Pet Lover!"

        // Navigation to Add Pet
        findViewById<Button>(R.id.buttonAddPet).setOnClickListener {
            startActivity(Intent(this, AddEditPetActivity::class.java))
        }

        // Navigation to Add Task
        findViewById<Button>(R.id.buttonManageTasks).setOnClickListener {
            startActivity(Intent(this, AddTaskActivity::class.java))
        }
    }
}
