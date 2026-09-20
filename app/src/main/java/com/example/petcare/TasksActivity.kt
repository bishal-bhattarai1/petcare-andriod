package com.example.petcare

import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import kotlin.math.roundToInt

class TasksActivity : AppCompatActivity() {
    private lateinit var database: AuthDatabaseHelper

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_tasks)
        database = AuthDatabaseHelper(this)

        WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightStatusBars = true
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main_tasks)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, 0)
            insets
        }

        setupActions()
        setupBottomNavigation()
    }

    override fun onResume() {
        super.onResume()
        renderTasks()
    }

    private fun setupActions() {
        val openAddTask = View.OnClickListener {
            startActivity(Intent(this, AddTaskActivity::class.java))
        }
        findViewById<View>(R.id.buttonAddTask).setOnClickListener(openAddTask)
        findViewById<FloatingActionButton>(R.id.fabAdd).setOnClickListener(openAddTask)
    }

    private fun setupBottomNavigation() {
        findViewById<View>(R.id.navHome).setOnClickListener {
            openMainTab(DashboardActivity::class.java)
        }
        findViewById<View>(R.id.navTasks).setOnClickListener {
            // Already on Tasks.
        }
        findViewById<View>(R.id.navExpenses).setOnClickListener {
            openMainTab(ExpensesActivity::class.java)
        }
        findViewById<View>(R.id.navProfile).setOnClickListener {
            openMainTab(ProfileActivity::class.java)
        }
    }

    private fun renderTasks() {
        val tasks = database.getCareTasks()
        val activeTasks = tasks.filter { !it.isCompleted }
        val completedTasks = tasks.filter { it.isCompleted }

        val taskLayout = findViewById<LinearLayout>(R.id.layoutTaskItems)
        taskLayout.removeAllViews()
        activeTasks.forEach { taskLayout.addView(createTaskRow(it)) }
        findViewById<TextView>(R.id.textEmptyTasks).visibility =
            if (activeTasks.isEmpty()) View.VISIBLE else View.GONE

        val completedLayout = findViewById<LinearLayout>(R.id.layoutCompletedTasks)
        completedLayout.removeAllViews()
        completedTasks.forEach { completedLayout.addView(createTaskRow(it)) }
        findViewById<TextView>(R.id.textEmptyCompletedTasks).visibility =
            if (completedTasks.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun createTaskRow(task: CareTask): View {
        val card = MaterialCardView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = 10.dp() }
            radius = 12.dp().toFloat()
            cardElevation = 0f
            strokeWidth = 0
            setCardBackgroundColor(ContextCompat.getColor(this@TasksActivity, R.color.card_bg))
            setOnClickListener { openChecklist(task) }
        }

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16.dp(), 14.dp(), 16.dp(), 14.dp())
        }

        val topRow = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            orientation = LinearLayout.HORIZONTAL
        }

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        content.addView(TextView(this).apply {
            text = task.description.ifBlank { "Care task" }
            setTextColor(ContextCompat.getColor(this@TasksActivity, R.color.app_text_primary))
            textSize = 15f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        })
        content.addView(TextView(this).apply {
            text = listOf(task.petName, task.scheduledTime.ifBlank { "No time" }, task.repeatType)
                .filter { it.isNotBlank() }
                .joinToString(" - ")
            setTextColor(ContextCompat.getColor(this@TasksActivity, R.color.app_text_secondary))
            textSize = 12f
            setPadding(0, 4.dp(), 0, 0)
        })

        topRow.addView(content)
        topRow.addView(TextView(this).apply {
            text = ">"
            gravity = Gravity.CENTER
            setTextColor(ContextCompat.getColor(this@TasksActivity, R.color.app_text_secondary))
            textSize = 22f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            layoutParams = LinearLayout.LayoutParams(32.dp(), 40.dp())
        })
        container.addView(topRow)

        val actionRow = LinearLayout(this).apply {
            gravity = Gravity.END
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 12.dp(), 0, 0)
        }
        actionRow.addView(MaterialButton(this).apply {
            text = if (task.isCompleted) "Undo" else "Complete"
            setTextColor(ContextCompat.getColor(this@TasksActivity, R.color.white))
            backgroundTintList = ContextCompat.getColorStateList(this@TasksActivity, R.color.black)
            cornerRadius = 8.dp()
            minHeight = 0
            insetTop = 0
            insetBottom = 0
            layoutParams = LinearLayout.LayoutParams(120.dp(), 42.dp())
            setOnClickListener {
                val saved = database.updateTaskCompletion(task.id, !task.isCompleted)
                Toast.makeText(
                    this@TasksActivity,
                    if (saved) "Task updated" else "Could not update task",
                    Toast.LENGTH_SHORT
                ).show()
                renderTasks()
            }
        })
        actionRow.addView(MaterialButton(this).apply {
            text = "Checklist"
            setTextColor(ContextCompat.getColor(this@TasksActivity, R.color.black))
            backgroundTintList = ContextCompat.getColorStateList(this@TasksActivity, android.R.color.transparent)
            strokeColor = ContextCompat.getColorStateList(this@TasksActivity, R.color.app_divider)
            strokeWidth = 1.dp()
            cornerRadius = 8.dp()
            minHeight = 0
            insetTop = 0
            insetBottom = 0
            layoutParams = LinearLayout.LayoutParams(112.dp(), 42.dp()).apply { marginStart = 8.dp() }
            setOnClickListener { openChecklist(task) }
        })
        container.addView(actionRow)

        card.addView(container)
        return card
    }

    private fun openChecklist(task: CareTask) {
        startActivity(
            Intent(this, ChecklistActivity::class.java)
                .putExtra(ChecklistActivity.EXTRA_PET_ID, task.petId)
                .putExtra(ChecklistActivity.EXTRA_PET_NAME, task.petName)
        )
    }

    private fun Int.dp(): Int = (this * resources.displayMetrics.density).roundToInt()

}
