package com.example.petcare

import android.content.Intent
import android.os.Bundle
import android.view.Gravity
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
import com.google.android.material.card.MaterialCardView
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlin.math.max
import kotlin.math.roundToInt

class NotificationsActivity : AppCompatActivity() {
    private lateinit var database: AuthDatabaseHelper

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_notifications)
        database = AuthDatabaseHelper(this)

        updateStatusBarIcons()
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main_notifications)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, 0)
            insets
        }

        setupBottomNavigation()
    }

    override fun onResume() {
        super.onResume()
        renderReminders()
    }

    private fun setupBottomNavigation() {
        findViewById<View>(R.id.navHome).setOnClickListener {
            openMainTab(DashboardActivity::class.java)
        }
        findViewById<View>(R.id.navTasks).setOnClickListener {
            openMainTab(TasksActivity::class.java)
        }
        findViewById<View>(R.id.navExpenses).setOnClickListener {
            openMainTab(ExpensesActivity::class.java)
        }
        findViewById<View>(R.id.navProfile).setOnClickListener {
            openMainTab(ProfileActivity::class.java)
        }
    }

    private fun renderReminders() {
        val allTasks = database.getCareTasks()
        val activeTasks = allTasks
            .filter { !it.isCompleted }
            .map { task -> task to task.reminderState() }
            .sortedWith(
                compareBy<Pair<CareTask, ReminderState>> { it.second.priority }
                    .thenBy { it.first.minutesUntilScheduled() ?: Int.MAX_VALUE }
            )

        val todayLayout = findViewById<LinearLayout>(R.id.layoutTodayReminders)
        todayLayout.removeAllViews()
        activeTasks.forEach { (task, state) ->
            todayLayout.addView(createReminderRow(task, state))
        }
        findViewById<TextView>(R.id.textEmptyToday).visibility =
            if (activeTasks.isEmpty()) View.VISIBLE else View.GONE

        // Healthcare History
        val medicalHistory = database.getAllHealthcareHistory()
        val healthcareLayout = findViewById<LinearLayout>(R.id.layoutHealthcareRecords)
        healthcareLayout.removeAllViews()
        medicalHistory.forEach { record ->
            healthcareLayout.addView(createHealthcareHistoryRow(record))
        }
        findViewById<TextView>(R.id.textEmptyHealthcare).visibility =
            if (medicalHistory.isEmpty()) View.VISIBLE else View.GONE

        val yesterdayLayout = findViewById<LinearLayout>(R.id.layoutYesterdayLog)
        yesterdayLayout.removeAllViews()
        allTasks.forEach { task ->
            yesterdayLayout.addView(createHistoryRow(task))
        }
        findViewById<TextView>(R.id.textEmptyYesterday).visibility =
            if (allTasks.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun createHealthcareHistoryRow(record: HealthcareRecord): View {
        val row = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            orientation = LinearLayout.HORIZONTAL
            setPadding(2.dp(), 12.dp(), 2.dp(), 12.dp())
        }

        row.addView(ImageView(this).apply {
            setImageResource(android.R.drawable.checkbox_on_background)
            setColorFilter(ContextCompat.getColor(this@NotificationsActivity, R.color.status_green))
            layoutParams = LinearLayout.LayoutParams(24.dp(), 24.dp())
        })

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            setPadding(12.dp(), 0, 12.dp(), 0)
        }
        content.addView(TextView(this).apply {
            text = "${record.petName} • ${record.type}"
            setTextColor(ContextCompat.getColor(this@NotificationsActivity, R.color.app_text_primary))
            textSize = 14f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        })
        content.addView(TextView(this).apply {
            text = record.date
            setTextColor(ContextCompat.getColor(this@NotificationsActivity, R.color.app_text_secondary))
            textSize = 12f
            setPadding(0, 3.dp(), 0, 0)
        })
        row.addView(content)

        if (record.notes.isNotBlank()) {
            row.addView(TextView(this).apply {
                text = "Notes"
                setTextColor(ContextCompat.getColor(this@NotificationsActivity, R.color.app_text_secondary))
                textSize = 11f
                setTypeface(null, android.graphics.Typeface.ITALIC)
            })
        }

        return row
    }

    private fun createReminderRow(task: CareTask, state: ReminderState): View {
        val isOverdue = state == ReminderState.OVERDUE
        val card = MaterialCardView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = 10.dp() }
            radius = 12.dp().toFloat()
            cardElevation = 0f
            strokeWidth = 0
            setCardBackgroundColor(
                ContextCompat.getColor(
                    this@NotificationsActivity,
                    if (isOverdue) R.color.app_accent_red else R.color.card_bg
                )
            )
        }

        val row = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            orientation = LinearLayout.HORIZONTAL
            setPadding(16.dp(), 14.dp(), 12.dp(), 14.dp())
        }

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        val titleColor = ContextCompat.getColor(this, if (isOverdue) R.color.white else R.color.app_text_primary)
        val subColor = ContextCompat.getColor(this, if (isOverdue) R.color.white else R.color.app_text_secondary)
        content.addView(TextView(this).apply {
            text = "${task.petName}'s ${task.description.ifBlank { "care task" }}"
            setTextColor(titleColor)
            textSize = 15f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        })
        content.addView(TextView(this).apply {
            text = state.label(task)
            setTextColor(subColor)
            textSize = 13f
            setPadding(0, 4.dp(), 0, 0)
        })

        row.addView(content)
        row.addView(TextView(this).apply {
            text = ">"
            gravity = Gravity.CENTER
            setTextColor(titleColor)
            textSize = 22f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            layoutParams = LinearLayout.LayoutParams(40.dp(), 40.dp())
        })

        card.setOnClickListener { openChecklist(task) }
        card.addView(row)
        return card
    }

    private fun createHistoryRow(task: CareTask): View {
        val row = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            orientation = LinearLayout.HORIZONTAL
            setPadding(2.dp(), 12.dp(), 2.dp(), 12.dp())
            setOnClickListener { openChecklist(task) }
        }

        row.addView(ImageView(this).apply {
            setImageResource(if (task.isCompleted) android.R.drawable.checkbox_on_background else android.R.drawable.presence_invisible)
            setColorFilter(
                ContextCompat.getColor(
                    this@NotificationsActivity,
                    if (task.isCompleted) R.color.status_green else R.color.app_text_secondary
                )
            )
            layoutParams = LinearLayout.LayoutParams(24.dp(), 24.dp())
        })

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            setPadding(12.dp(), 0, 12.dp(), 0)
        }
        content.addView(TextView(this).apply {
            text = task.description.ifBlank { "Care task" }
            setTextColor(ContextCompat.getColor(this@NotificationsActivity, R.color.app_text_primary))
            textSize = 14f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        })
        content.addView(TextView(this).apply {
            text = "${task.petName} - ${task.scheduledTime.ifBlank { "No time" }}"
            setTextColor(ContextCompat.getColor(this@NotificationsActivity, R.color.app_text_secondary))
            textSize = 12f
            setPadding(0, 3.dp(), 0, 0)
        })
        row.addView(content)

        row.addView(TextView(this).apply {
            text = if (task.isCompleted) "Done" else "Missed"
            setTextColor(
                ContextCompat.getColor(
                    this@NotificationsActivity,
                    if (task.isCompleted) R.color.status_green else R.color.app_text_secondary
                )
            )
            textSize = 12f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        })

        return row
    }

    private fun openChecklist(task: CareTask) {
        startActivity(
            Intent(this, ChecklistActivity::class.java)
                .putExtra(ChecklistActivity.EXTRA_PET_ID, task.petId)
                .putExtra(ChecklistActivity.EXTRA_PET_NAME, task.petName)
        )
    }

    private fun CareTask.reminderState(): ReminderState {
        val minutes = minutesUntilScheduled() ?: return ReminderState.SCHEDULED
        return when {
            minutes < 0 -> ReminderState.OVERDUE
            minutes <= 60 -> ReminderState.UPCOMING
            else -> ReminderState.SCHEDULED
        }
    }

    private fun ReminderState.label(task: CareTask): String {
        val minutes = task.minutesUntilScheduled()
        return when (this) {
            ReminderState.OVERDUE -> "Overdue - ${task.scheduledTime.ifBlank { "Today" }}"
            ReminderState.UPCOMING -> "Upcoming - in ${max(minutes ?: 0, 1)} minutes"
            ReminderState.SCHEDULED -> task.scheduledTime.ifBlank { "Scheduled later today" }
        }
    }

    private fun CareTask.minutesUntilScheduled(): Int? {
        val time = scheduledTime.trim()
        if (time.isBlank()) return null

        return try {
            val parsed = SimpleDateFormat("hh:mm a", Locale.getDefault()).parse(time) ?: return null
            val now = Calendar.getInstance()
            val scheduled = Calendar.getInstance().apply {
                timeInMillis = now.timeInMillis
                val parsedCalendar = Calendar.getInstance().apply { setTime(parsed) }
                set(Calendar.HOUR_OF_DAY, parsedCalendar.get(Calendar.HOUR_OF_DAY))
                set(Calendar.MINUTE, parsedCalendar.get(Calendar.MINUTE))
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            ((scheduled.timeInMillis - now.timeInMillis) / 60000.0).roundToInt()
        } catch (_: Exception) {
            null
        }
    }

    private fun Int.dp(): Int = (this * resources.displayMetrics.density).roundToInt()

    private fun updateStatusBarIcons() {
        val isDarkMode = (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES
        WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightStatusBars = !isDarkMode
    }

    private enum class ReminderState(val priority: Int) {
        OVERDUE(0),
        UPCOMING(1),
        SCHEDULED(2)
    }
}
