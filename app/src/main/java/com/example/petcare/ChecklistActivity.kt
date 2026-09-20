package com.example.petcare

import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import java.util.Calendar
import java.util.Locale
import kotlin.math.roundToInt

class ChecklistActivity : AppCompatActivity() {
    private lateinit var database: AuthDatabaseHelper
    private var petId: Long = -1L
    private var petName: String = "Pet"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_checklist)
        database = AuthDatabaseHelper(this)
        petId = intent.getLongExtra(EXTRA_PET_ID, -1L)
        petName = intent.getStringExtra(EXTRA_PET_NAME).orEmpty().ifBlank { "Pet" }

        WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightStatusBars = true
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(android.R.id.content)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        toolbar.setNavigationOnClickListener { finish() }
        findViewById<TextView>(R.id.textChecklistTitle).text = "$petName checklist"
        findViewById<FloatingActionButton>(R.id.fabAddTask).setOnClickListener {
            startActivity(
                android.content.Intent(this, AddTaskActivity::class.java)
                    .putExtra(AddTaskActivity.EXTRA_SELECTED_PET_ID, petId)
            )
        }
    }

    override fun onResume() {
        super.onResume()
        renderChecklist()
    }

    private fun renderChecklist() {
        val tasks = database.getCareTasks(if (petId > 0) petId else null)
        val layout = findViewById<LinearLayout>(R.id.layoutChecklistItems)
        layout.removeAllViews()
        tasks.forEach { task -> layout.addView(createTaskRow(task)) }
        findViewById<TextView>(R.id.textEmptyChecklist).visibility =
            if (tasks.isEmpty()) View.VISIBLE else View.GONE
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
            setCardBackgroundColor(ContextCompat.getColor(this@ChecklistActivity, R.color.card_bg))
        }

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16.dp(), 14.dp(), 16.dp(), 14.dp())
        }

        val top = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            orientation = LinearLayout.HORIZONTAL
        }

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        content.addView(TextView(this).apply {
            text = task.description.ifBlank { "Care task" }
            setTextColor(ContextCompat.getColor(this@ChecklistActivity, if (task.isCompleted) R.color.app_text_secondary else R.color.app_text_primary))
            textSize = 15f
            if (task.isCompleted) {
                paintFlags = paintFlags or android.graphics.Paint.STRIKE_THRU_TEXT_FLAG
                typeface = android.graphics.Typeface.DEFAULT
            } else {
                typeface = android.graphics.Typeface.DEFAULT_BOLD
            }
        })
        content.addView(TextView(this).apply {
            text = listOf(task.category, task.scheduledTime.ifBlank { "No time" }, task.repeatType)
                .filter { it.isNotBlank() }
                .joinToString(" - ")
            setTextColor(ContextCompat.getColor(this@ChecklistActivity, R.color.app_text_secondary))
            textSize = 12f
            setPadding(0, 4.dp(), 0, 0)
        })
        top.addView(content)
        top.addView(TextView(this).apply {
            text = if (task.isCompleted) "Done" else "Open"
            setTextColor(
                ContextCompat.getColor(
                    this@ChecklistActivity,
                    if (task.isCompleted) R.color.status_green else R.color.app_text_secondary
                )
            )
            textSize = 12f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        })
        container.addView(top)

        // Granular Weekly Checklist
        if (task.repeatType == "Weekly" && !task.isCompleted) {
            val weekDaysList = task.weekDays.split(",").filter { it.isNotBlank() }
            val completedDays = task.completedWeekDays.split(",").toMutableSet()
            
            if (weekDaysList.isNotEmpty()) {
                val weeklyHeader = TextView(this).apply {
                    text = "Weekly Checklist"
                    textSize = 13f
                    typeface = android.graphics.Typeface.DEFAULT_BOLD
                    setTextColor(ContextCompat.getColor(this@ChecklistActivity, R.color.app_text_primary))
                    setPadding(0, 16.dp(), 0, 8.dp())
                }
                container.addView(weeklyHeader)

                weekDaysList.forEach { day ->
                    val dayRow = LinearLayout(this).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = Gravity.CENTER_VERTICAL
                        setPadding(0, 4.dp(), 0, 4.dp())
                    }
                    val cb = android.widget.CheckBox(this).apply {
                        isChecked = completedDays.contains(day)
                        text = when(day) {
                            "Mon" -> "Monday"
                            "Tue" -> "Tuesday"
                            "Wed" -> "Wednesday"
                            "Thu" -> "Thursday"
                            "Fri" -> "Friday"
                            "Sat" -> "Saturday"
                            "Sun" -> "Sunday"
                            else -> day
                        }
                        setTextColor(ContextCompat.getColor(this@ChecklistActivity, R.color.app_text_primary))
                        setOnCheckedChangeListener { _, isChecked ->
                            if (isChecked) completedDays.add(day) else completedDays.remove(day)
                            database.updateWeeklyDayCompletion(task.id, completedDays.filter { it.isNotBlank() }.joinToString(","))
                        }
                    }
                    dayRow.addView(cb)
                    container.addView(dayRow)
                }
            }
        }

        val actions = LinearLayout(this).apply {
            gravity = Gravity.END
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 12.dp(), 0, 0)
        }

        val isTimeMet = isScheduleMet(task)
        
        if (!task.isCompleted && isTimeMet) {
            actions.addView(MaterialButton(this).apply {
                text = "Complete Routine"
                setTextColor(ContextCompat.getColor(this@ChecklistActivity, R.color.white))
                backgroundTintList = ContextCompat.getColorStateList(this@ChecklistActivity, R.color.black)
                cornerRadius = 8.dp()
                minHeight = 0
                insetTop = 0
                insetBottom = 0
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, 
                    44.dp()
                ).apply { marginStart = 8.dp() }
                setOnClickListener {
                    val saved = database.updateTaskCompletion(task.id, true)
                    if (saved) {
                        Toast.makeText(this@ChecklistActivity, "Routine completed! 🎉", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(this@ChecklistActivity, "Could not complete routine", Toast.LENGTH_SHORT).show()
                    }
                    renderChecklist()
                }
            })
        }
        container.addView(actions)

        if (!task.isCompleted && !isTimeMet) {
            val remarkText = getScheduleRemark(task)
            val remarkBadge = TextView(this).apply {
                text = remarkText
                setTextColor(ContextCompat.getColor(this@ChecklistActivity, R.color.app_accent_red))
                textSize = 12f
                setTypeface(null, android.graphics.Typeface.ITALIC)
                setPadding(0, 12.dp(), 0, 0)
            }
            container.addView(remarkBadge)
        }

        if (task.isCompleted) {
            val completedBadge = TextView(this).apply {
                text = "Task Completed"
                setTextColor(ContextCompat.getColor(this@ChecklistActivity, R.color.status_green))
                textSize = 13f
                typeface = android.graphics.Typeface.DEFAULT_BOLD
                setPadding(0, 8.dp(), 0, 0)
            }
            container.addView(completedBadge)
        }

        card.addView(container)
        return card
    }

    private fun isScheduleMet(task: CareTask): Boolean {
        // 1. Day Check for Weekly
        if (task.repeatType == "Weekly") {
            val today = Calendar.getInstance().getDisplayName(Calendar.DAY_OF_WEEK, Calendar.SHORT, Locale.ENGLISH) ?: ""
            if (!task.weekDays.contains(today, ignoreCase = true)) return false
        }

        // 2. Time Check
        val scheduled = task.scheduledTime
        if (scheduled.isBlank() || scheduled.equals("No time", true)) return true

        return try {
            val calendar = Calendar.getInstance()
            val currentHour = calendar.get(Calendar.HOUR_OF_DAY)
            val currentMin = calendar.get(Calendar.MINUTE)

            val parts = scheduled.split(" ") // ["02:00", "PM"]
            val timeParts = parts[0].split(":") // ["02", "00"]
            var sHour = timeParts[0].toInt()
            val sMin = timeParts[1].toInt()
            val amPm = parts[1]

            if (amPm.equals("PM", true) && sHour < 12) sHour += 12
            if (amPm.equals("AM", true) && sHour == 12) sHour = 0

            when {
                currentHour > sHour -> true
                currentHour == sHour -> currentMin >= sMin
                else -> false
            }
        } catch (e: Exception) {
            true // Fallback to allow completion if parsing fails
        }
    }

    private fun getScheduleRemark(task: CareTask): String {
        if (task.repeatType == "Weekly") {
            val today = Calendar.getInstance().getDisplayName(Calendar.DAY_OF_WEEK, Calendar.SHORT, Locale.ENGLISH) ?: ""
            if (!task.weekDays.contains(today, ignoreCase = true)) {
                return "Scheduled for ${task.weekDays}"
            }
        }
        return "Available at ${task.scheduledTime}"
    }

    private fun Int.dp(): Int = (this * resources.displayMetrics.density).roundToInt()

    companion object {
        const val EXTRA_PET_ID = "extra_pet_id"
        const val EXTRA_PET_NAME = "extra_pet_name"
    }
}
