package com.example.petcare

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton

class DashboardActivity : AppCompatActivity() {
    private lateinit var database: AuthDatabaseHelper
    private lateinit var sessionManager: SessionManager
    private lateinit var recyclerView: RecyclerView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_dashboard)
        database = AuthDatabaseHelper(this)
        sessionManager = SessionManager(this)

        WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightStatusBars = true

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main_dashboard)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, 0)
            insets
        }

        initUI()
        setupDynamicGreeting()
    }

    override fun onResume() {
        super.onResume()
        loadPetsFromDatabase()
    }

    private fun setupDynamicGreeting() {
        val name = sessionManager.getUserName() ?: "Pet Lover"
        findViewById<TextView>(R.id.textUserName).text = name

        val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
        val greeting = when (hour) {
            in 5..11 -> "Good morning"
            in 12..16 -> "Good afternoon"
            in 17..20 -> "Good evening"
            else -> "Good night"
        }
        findViewById<TextView>(R.id.textGreetingLabel).text = greeting
    }

    private fun initUI() {
        recyclerView = findViewById(R.id.recyclerViewPets)
        recyclerView.layoutManager = LinearLayoutManager(this)

        loadPetsFromDatabase()

        findViewById<View>(R.id.cardAddPet).setOnClickListener {
            startActivity(Intent(this, AddEditPetActivity::class.java))
        }

        findViewById<FloatingActionButton>(R.id.fabAdd).setOnClickListener {
            startActivity(Intent(this, AddEditPetActivity::class.java))
        }
    }

    private fun loadPetsFromDatabase() {
        val pets = database.getAllPets()
        recyclerView.adapter = PetAdapter(pets)
        findViewById<TextView>(R.id.textPetCount).text = "${pets.size} pets"
    }
}
