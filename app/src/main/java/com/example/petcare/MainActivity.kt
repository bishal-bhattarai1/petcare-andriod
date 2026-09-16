package com.example.petcare

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.petcare.data.SessionManager

class MainActivity : AppCompatActivity() {
    private lateinit var sessionManager: SessionManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        sessionManager = SessionManager(this)
        if (sessionManager.isLoggedIn()) {
            val intent = Intent(this, DashboardActivity::class.java)
            startActivity(intent)
            finish()
            return
        }

        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        val paddingDp = (28 * resources.displayMetrics.density).toInt()
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(paddingDp + systemBars.left, systemBars.top, paddingDp + systemBars.right, systemBars.bottom)
            insets
        }

        findViewById<Button>(R.id.buttonLogin).setOnClickListener {
            val intent = Intent(this, LoginActivity::class.java)
            startActivity(intent)
        }

        findViewById<Button>(R.id.buttonSignUp).setOnClickListener {
            val intent = Intent(this, SignUpActivity::class.java)
            startActivity(intent)
        }
    }
}