package com.example.petcare

import android.content.Intent
import android.graphics.Typeface
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
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.math.max
import kotlin.math.roundToInt

class NotificationsActivity : AppCompatActivity() {
    private lateinit var database: AuthDatabaseHelper
    private val executor: ExecutorService = Executors.newSingleThreadExecutor()

    private lateinit var todayLayout: LinearLayout
    private lateinit var healthcareLayout: LinearLayout
    private lateinit var yesterdayLayout: LinearLayout
    private lateinit var emptyToday: TextView
    private lateinit var emptyHealthcare: TextView
    private lateinit var emptyYesterday: TextView

    // Resolved once instead of per row.
    private val colorTextPrimary by lazy { ContextCompat.getColor(this, R.color.app_text_primary) }
    private val colorTextSecondary by lazy { ContextCompat.getColor(this, R.color.app_text_secondary) }
    private val colorGreen by lazy { ContextCompat.getColor(this, R.color.status_green) }
    private val colorRed by lazy { ContextCompat.getColor(this, R.color.app_accent_red) }
    private val colorCard by lazy { ContextCompat.getColor(this, R.color.card_bg) }
    private val colorWhite by lazy { ContextCompat.getColor(this, R.color.white) }
    private val fontBold by lazy { figtree(Typeface.BOLD) }
    private val fontItalic by lazy { figtree(Typeface.ITALIC) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_notifications)
        database = AuthDatabaseHelper(this)

        todayLayout = findViewById(R.id.layoutTodayReminders)
        healthcareLayout = findViewById(R.id.layoutHealthcareRecords)
        yesterdayLayout = findViewById(R.id.layoutYesterdayLog)
        emptyToday = findViewById(R.id.textEmptyToday)
        emptyHealthcare = findViewById(R.id.textEmptyHealthcare)
        emptyYesterday = findViewById(R.id.textEmptyYesterday)

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
        loadReminders()
    }

    override fun onDestroy() {
        executor.shutdownNow()
        super.onDestroy()
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

    /** Queries the database and prepares reminders off the main thread, then renders on it. */
    private fun loadReminders() {
        if (executor.isShutdown) return
        executor.execute {
            val allTasks = database.getCareTasks()
            val medicalHistory = database.getAllHealthcareHistory()
            val reminders = buildReminders(allTasks)
            runOnUiThread {
                if (!isFinishing && !isDestroyed) render(reminders, medicalHistory, allTasks)
            }
        }
    }

    private fun buildReminders(allTasks: List<CareTask>): List<Reminder> {
        // Parse each scheduled time exactly once, against a single "now".
        val formatter = SimpleDateFormat("hh:mm a", Locale.getDefault())
        val now = Calendar.getInstance()
        return allTasks
            .filter { !it.isCompleted }
            .map { task ->
                val minutes = task.minutesUntilScheduled(formatter, now)
                Reminder(task, minutes, reminderState(minutes))
            }
            .sortedWith(
                compareBy<Reminder> { it.state.priority }
                    .thenBy { it.minutesUntil ?: Int.MAX_VALUE }
            )
    }

    private fun render(
        reminders: List<Reminder>,
        medicalHistory: List<HealthcareRecord>,
        allTasks: List<CareTask>
    ) {
        todayLayout.removeAllViews()
        reminders.forEach { todayLayout.addView(createReminderRow(it)) }
        emptyToday.visibility = if (reminders.isEmpty()) View.VISIBLE else View.GONE

        healthcareLayout.removeAllViews()
        medicalHistory.forEach { healthcareLayout.addView(createHealthcareHistoryRow(it)) }
        emptyHealthcare.visibility = if (medicalHistory.isEmpty()) View.VISIBLE else View.GONE

        yesterdayLayout.removeAllViews()
        allTasks.forEach { yesterdayLayout.addView(createHistoryRow(it)) }
        emptyYesterday.visibility = if (allTasks.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun createHealthcareHistoryRow(record: HealthcareRecord): View {
        val row = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            orientation = LinearLayout.HORIZONTAL
            setPadding(2.dp(), 12.dp(), 2.dp(), 12.dp())
        }

        row.addView(ImageView(this).apply {
            setImageResource(android.R.drawable.checkbox_on_background)
            setColorFilter(colorGreen)
            layoutParams = LinearLayout.LayoutParams(24.dp(), 24.dp())
        })

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            setPadding(12.dp(), 0, 12.dp(), 0)
        }
        content.addView(TextView(this).apply {
            text = "${record.petName} • ${record.type}"
            setTextColor(colorTextPrimary)
            textSize = 14f
            typeface = fontBold
        })
        content.addView(TextView(this).apply {
            text = record.date
            setTextColor(colorTextSecondary)
            textSize = 12f
            setPadding(0, 3.dp(), 0, 0)
        })
        row.addView(content)

        if (record.notes.isNotBlank()) {
            row.addView(TextView(this).apply {
                text = "Notes"
                setTextColor(colorTextSecondary)
                textSize = 11f
                typeface = fontItalic
            })
        }

        return row
    }

    private fun createReminderRow(reminder: Reminder): View {
        val task = reminder.task
        val isOverdue = reminder.state == ReminderState.OVERDUE
        val card = MaterialCardView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = 10.dp() }
            radius = 20.dp().toFloat()
            cardElevation = 0f
            strokeWidth = 0
            setCardBackgroundColor(if (isOverdue) colorRed else colorCard)
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

        val titleColor = if (isOverdue) colorWhite else colorTextPrimary
        val subColor = if (isOverdue) colorWhite else colorTextSecondary
        content.addView(TextView(this).apply {
            text = "${task.petName}'s ${task.description.ifBlank { "care task" }}"
            setTextColor(titleColor)
            textSize = 15f
            typeface = fontBold
        })
        content.addView(TextView(this).apply {
            text = reminder.label()
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
            typeface = fontBold
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

        val statusColor = if (task.isCompleted) colorGreen else colorTextSecondary
        row.addView(ImageView(this).apply {
            setImageResource(if (task.isCompleted) android.R.drawable.checkbox_on_background else android.R.drawable.presence_invisible)
            setColorFilter(statusColor)
            layoutParams = LinearLayout.LayoutParams(24.dp(), 24.dp())
        })

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            setPadding(12.dp(), 0, 12.dp(), 0)
        }
        content.addView(TextView(this).apply {
            text = task.description.ifBlank { "Care task" }
            setTextColor(colorTextPrimary)
            textSize = 14f
            typeface = fontBold
        })
        content.addView(TextView(this).apply {
            text = "${task.petName} - ${task.scheduledTime.ifBlank { "No time" }}"
            setTextColor(colorTextSecondary)
            textSize = 12f
            setPadding(0, 3.dp(), 0, 0)
        })
        row.addView(content)

        row.addView(TextView(this).apply {
            text = if (task.isCompleted) "Done" else "Missed"
            setTextColor(statusColor)
            textSize = 12f
            typeface = fontBold
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

    private fun reminderState(minutes: Int?): ReminderState = when {
        minutes == null -> ReminderState.SCHEDULED
        minutes < 0 -> ReminderState.OVERDUE
        minutes <= 60 -> ReminderState.UPCOMING
        else -> ReminderState.SCHEDULED
    }

    private fun Reminder.label(): String = when (state) {
        ReminderState.OVERDUE -> "Overdue - ${task.scheduledTime.ifBlank { "Today" }}"
        ReminderState.UPCOMING -> "Upcoming - in ${max(minutesUntil ?: 0, 1)} minutes"
        ReminderState.SCHEDULED -> task.scheduledTime.ifBlank { "Scheduled later today" }
    }

    private fun CareTask.minutesUntilScheduled(formatter: SimpleDateFormat, now: Calendar): Int? {
        val time = scheduledTime.trim()
        if (time.isBlank()) return null

        return try {
            val parsed = formatter.parse(time) ?: return null
            val parsedCalendar = Calendar.getInstance().apply { setTime(parsed) }
            val scheduled = (now.clone() as Calendar).apply {
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

    private data class Reminder(val task: CareTask, val minutesUntil: Int?, val state: ReminderState)

    private enum class ReminderState(val priority: Int) {
        OVERDUE(0),
        UPCOMING(1),
        SCHEDULED(2)
    }
}
