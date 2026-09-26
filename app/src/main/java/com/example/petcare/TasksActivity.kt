package com.example.petcare

import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.google.android.material.floatingactionbutton.FloatingActionButton

class TasksActivity : AppCompatActivity() {
    private lateinit var tasksPage: TasksPageController

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_tasks)

        tasksPage = TasksPageController(
            activity = this,
            page = findViewById(R.id.main_tasks),
            database = AuthDatabaseHelper(this),
            sessionManager = SessionManager(this)
        )

        updateStatusBarIcons()
        updateBottomNavigationUI()

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main_tasks)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, 0)
            insets
        }

        tasksPage.setup()
        findViewById<FloatingActionButton>(R.id.fabAdd).setOnClickListener {
            startActivity(Intent(this, AddTaskActivity::class.java))
        }
        setupBottomNavigation()
    }

    override fun onResume() {
        super.onResume()
        tasksPage.refresh()
    }

    private fun setupBottomNavigation() {
        findViewById<View>(R.id.navHome).setOnClickListener {
            openMainTab(DashboardActivity::class.java)
        }
        findViewById<View>(R.id.navTasks).setOnClickListener {
            // Already on Tasks
        }
        findViewById<View>(R.id.navExpenses).setOnClickListener {
            openMainTab(ExpensesActivity::class.java)
        }
        findViewById<View>(R.id.navProfile).setOnClickListener {
            openMainTab(ProfileActivity::class.java)
        }
    }

    private fun updateStatusBarIcons() {
        val isDarkMode = (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES
        WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightStatusBars = !isDarkMode
    }

    private fun updateBottomNavigationUI() {
        val navItems = listOf(
            R.id.navHome to false,
            R.id.navTasks to true,
            R.id.navExpenses to false,
            R.id.navProfile to false
        )

        navItems.forEach { (viewId, selected) ->
            val item = findViewById<LinearLayout>(viewId)
            item.setBackgroundResource(if (selected) R.drawable.bg_bottom_nav_selected else 0)
            (item.layoutParams as? LinearLayout.LayoutParams)?.let { params ->
                val margin = if (selected) (4 * resources.displayMetrics.density).toInt() else 0
                params.marginStart = margin
                params.marginEnd = margin
                item.layoutParams = params
            }

            val color = ContextCompat.getColor(
                this,
                if (selected) R.color.auth_teal_primary else R.color.auth_text_grey
            )
            val image = item.getChildAt(0) as? ImageView
            val label = item.getChildAt(1) as? TextView
            image?.imageTintList = ColorStateList.valueOf(color)
            label?.setTextColor(color)
            label?.setTypeface(figtree(), if (selected) Typeface.BOLD else Typeface.NORMAL)
        }
    }
}
