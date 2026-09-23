package com.example.petcare

import android.app.TimePickerDialog
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.EditText
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
    private var searchQuery: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_checklist)
        database = AuthDatabaseHelper(this)
        petId = intent.getLongExtra(EXTRA_PET_ID, -1L)
        petName = intent.getStringExtra(EXTRA_PET_NAME).orEmpty().ifBlank { "Pet" }

        updateStatusBarIcons()
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

        findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.editChecklistSearch).addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
                searchQuery = s?.toString()?.trim().orEmpty()
                renderChecklist()
            }
        })
    }

    override fun onResume() {
        super.onResume()
        renderChecklist()
    }

    private fun renderChecklist() {
        var tasks = database.getCareTasks(if (petId > 0) petId else null)
        
        if (searchQuery.isNotBlank()) {
            tasks = tasks.filter { it.description.contains(searchQuery, ignoreCase = true) }
        }

        val layout = findViewById<LinearLayout>(R.id.layoutChecklistItems)
        layout.removeAllViews()
        tasks.forEach { task -> layout.addView(createTaskRow(task)) }
        
        val emptyText = findViewById<TextView>(R.id.textEmptyChecklist)
        emptyText.text = if (searchQuery.isBlank()) "No checklist items yet." else "No routines match your search."
        emptyText.visibility = if (tasks.isEmpty()) View.VISIBLE else View.GONE
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
                        val vibrator = getSystemService(VIBRATOR_SERVICE) as android.os.Vibrator
                        vibrator.vibrate(android.os.VibrationEffect.createOneShot(50, android.os.VibrationEffect.DEFAULT_AMPLITUDE))
                        
                        Toast.makeText(this@ChecklistActivity, "Routine completed! 🎉", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(this@ChecklistActivity, "Could not complete routine", Toast.LENGTH_SHORT).show()
                    }
                    renderChecklist()
                }
            })
        }

        if (!task.isCompleted) {
            actions.addView(MaterialButton(this).apply {
                text = "Edit"
                setTextColor(ContextCompat.getColor(this@ChecklistActivity, R.color.black))
                backgroundTintList = ContextCompat.getColorStateList(this@ChecklistActivity, android.R.color.transparent)
                strokeColor = ContextCompat.getColorStateList(this@ChecklistActivity, R.color.app_divider)
                strokeWidth = 1.dp()
                cornerRadius = 8.dp()
                minHeight = 0
                insetTop = 0
                insetBottom = 0
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    44.dp()
                ).apply { marginStart = 8.dp() }
                setOnClickListener { showEditDialog(task) }
            })

            actions.addView(MaterialButton(this).apply {
                text = "Delete"
                setTextColor(ContextCompat.getColor(this@ChecklistActivity, R.color.app_accent_red))
                backgroundTintList = ContextCompat.getColorStateList(this@ChecklistActivity, android.R.color.transparent)
                strokeColor = ContextCompat.getColorStateList(this@ChecklistActivity, R.color.app_divider)
                strokeWidth = 1.dp()
                cornerRadius = 8.dp()
                minHeight = 0
                insetTop = 0
                insetBottom = 0
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    44.dp()
                ).apply { marginStart = 8.dp() }
                setOnClickListener { showDeleteTaskConfirmation(task.id) }
            })

            actions.addView(MaterialButton(this).apply {
                text = "Sync Calendar"
                setTextColor(ContextCompat.getColor(this@ChecklistActivity, R.color.black))
                backgroundTintList = ContextCompat.getColorStateList(this@ChecklistActivity, android.R.color.transparent)
                strokeColor = ContextCompat.getColorStateList(this@ChecklistActivity, R.color.app_divider)
                strokeWidth = 1.dp()
                cornerRadius = 8.dp()
                minHeight = 0
                insetTop = 0
                insetBottom = 0
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    44.dp()
                ).apply { marginStart = 8.dp() }
                setOnClickListener { exportToSystemCalendar(task) }
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

    private fun showDeleteTaskConfirmation(taskId: Long) {
        AlertDialog.Builder(this)
            .setTitle("Delete Task")
            .setMessage("Are you sure you want to remove this care routine?")
            .setPositiveButton("Delete") { _, _ ->
                if (database.deleteTask(taskId)) {
                    Toast.makeText(this, "Task removed", Toast.LENGTH_SHORT).show()
                    renderChecklist()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showEditDialog(task: CareTask) {
        val form = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24.dp(), 16.dp(), 24.dp(), 0)
        }
        
        val descriptionInput = EditText(this).apply {
            hint = "Task title"
            setText(task.description)
            setSingleLine(true)
        }
        
        val timeInput = EditText(this).apply {
            hint = "Time"
            setText(task.scheduledTime)
            isFocusable = false
            setOnClickListener { showTimePicker(this) }
        }

        val suppliesInput = EditText(this).apply {
            hint = "Required supplies"
            setText(task.requiredSupplies)
        }

        val notesInput = EditText(this).apply {
            hint = "Special instructions"
            setText(task.taskNotes)
            minLines = 2
        }

        form.addView(TextView(this).apply { text = "Title & Time"; textSize = 12f; setPadding(0,0,0,4.dp()) })
        form.addView(descriptionInput)
        form.addView(timeInput)
        form.addView(TextView(this).apply { text = "Resources & Notes"; textSize = 12f; setPadding(0,16.dp(),0,4.dp()) })
        form.addView(suppliesInput)
        form.addView(notesInput)

        AlertDialog.Builder(this)
            .setTitle("Edit Care Routine")
            .setView(form)
            .setPositiveButton("Save Changes") { _, _ ->
                val description = descriptionInput.text.toString().trim()
                if (description.isBlank()) {
                    Toast.makeText(this, "Task title is required", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                val saved = database.updateTaskDetails(
                    task.id,
                    description,
                    timeInput.text.toString().trim(),
                    suppliesInput.text.toString().trim(),
                    notesInput.text.toString().trim()
                )
                Toast.makeText(
                    this,
                    if (saved) "Changes saved" else "Could not save task",
                    Toast.LENGTH_SHORT
                ).show()
                renderChecklist()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showTimePicker(input: EditText) {
        val calendar = Calendar.getInstance()
        TimePickerDialog(
            this,
            { _, hourOfDay, minute ->
                val suffix = if (hourOfDay >= 12) "PM" else "AM"
                val hour = when {
                    hourOfDay == 0 -> 12
                    hourOfDay > 12 -> hourOfDay - 12
                    else -> hourOfDay
                }
                input.setText(String.format(Locale.getDefault(), "%02d:%02d %s", hour, minute, suffix))
            },
            calendar.get(Calendar.HOUR_OF_DAY),
            calendar.get(Calendar.MINUTE),
            false
        ).show()
    }

    private fun exportToSystemCalendar(task: CareTask) {
        val intent = android.content.Intent(android.content.Intent.ACTION_INSERT)
            .setData(android.provider.CalendarContract.Events.CONTENT_URI)
            .putExtra(android.provider.CalendarContract.Events.TITLE, "PetCare: ${task.description} (${task.petName})")
            .putExtra(android.provider.CalendarContract.Events.DESCRIPTION, "Routine care task for ${task.petName}.\nNotes: ${task.taskNotes}\nSupplies: ${task.requiredSupplies}")
            .putExtra(android.provider.CalendarContract.Events.EVENT_LOCATION, task.locationName ?: "")
            .putExtra(android.provider.CalendarContract.EXTRA_EVENT_BEGIN_TIME, System.currentTimeMillis() + 60 * 60 * 1000) // Default 1 hour from now
            .putExtra(android.provider.CalendarContract.EXTRA_EVENT_END_TIME, System.currentTimeMillis() + 120 * 60 * 1000)
        
        try {
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "Calendar app not found", Toast.LENGTH_SHORT).show()
        }
    }

    private fun Int.dp(): Int = (this * resources.displayMetrics.density).roundToInt()

    private fun updateStatusBarIcons() {
        val isDarkMode = (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES
        WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightStatusBars = !isDarkMode
    }

    companion object {
        const val EXTRA_PET_ID = "extra_pet_id"
        const val EXTRA_PET_NAME = "extra_pet_name"
    }
}
