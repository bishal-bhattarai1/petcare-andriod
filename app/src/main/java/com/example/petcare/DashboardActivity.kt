package com.example.petcare

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.petcare.data.PetCareDatabase
import com.example.petcare.data.SessionManager

class DashboardActivity : AppCompatActivity() {
    private lateinit var sessionManager: SessionManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_dashboard)
        val paddingDp = (28 * resources.displayMetrics.density).toInt()
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main_dashboard)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(paddingDp + systemBars.left, systemBars.top, paddingDp + systemBars.right, systemBars.bottom)
            insets
        }

        sessionManager = SessionManager(this)
        val database = PetCareDatabase.getDatabase(this)
        val allUsers = database.userDao().allUsers
        android.util.Log.d("PetCareDB", "Verified DB Content - User Count: ${allUsers.size}")
        for (u in allUsers) {
            android.util.Log.d("PetCareDB", "User: ${u.name} | Email: ${u.email}")
        }

        findViewById<TextView>(R.id.textViewUserName).text = sessionManager.getUserName()
        findViewById<TextView>(R.id.textViewUserEmail).text = sessionManager.getUserEmail()

        findViewById<Button>(R.id.buttonLogout).setOnClickListener {
            sessionManager.logout()
            val intent = Intent(this, MainActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
        }
    }
}