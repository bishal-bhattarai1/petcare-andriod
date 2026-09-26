package com.example.petcare

import android.app.TimePickerDialog
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.CalendarContract
import android.text.Editable
import android.text.TextWatcher
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.View
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
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.math.roundToInt

class ChecklistActivity : AppCompatActivity() {
    private lateinit var database: AuthDatabaseHelper
    private val executor: ExecutorService = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())

    private var petId: Long = -1L
    private var selectedPetId: Long? = null
    private var petName: String = "Pet"
    private var searchQuery: String = ""

    /** Everything loaded from the database; pet filter and search are applied in memory. */
    private var allTasks: List<CareTask> = emptyList()
    private var pets: List<PetOption> = emptyList()

    private lateinit var adapter: ChecklistAdapter
    private lateinit var recycler: RecyclerView
    private lateinit var chipGroup: ChipGroup
    private lateinit var titleText: TextView
    private lateinit var subtitleText: TextView
    private lateinit var progressRow: View
    private lateinit var progress: LinearProgressIndicator
    private lateinit var percentText: TextView
    private lateinit var emptyText: TextView
    private lateinit var fab: FloatingActionButton

    private val searchRunnable = Runnable { applyFilters() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_checklist)
        database = AuthDatabaseHelper(this)
        petId = intent.getLongExtra(EXTRA_PET_ID, -1L)
        selectedPetId = if (savedInstanceState?.containsKey(STATE_PET_ID) == true) {
            savedInstanceState.getLong(STATE_PET_ID).takeIf { it > 0 }
        } else {
            petId.takeIf { it > 0 }
        }
        petName = intent.getStringExtra(EXTRA_PET_NAME).orEmpty().ifBlank { "Pet" }

        updateStatusBarIcons()
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(android.R.id.content)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        recycler = findViewById(R.id.recyclerChecklist)
        chipGroup = findViewById(R.id.chipGroupPets)
        titleText = findViewById(R.id.textChecklistTitle)
        subtitleText = findViewById(R.id.textChecklistSubtitle)
        progressRow = findViewById(R.id.layoutChecklistProgress)
        progress = findViewById(R.id.progressChecklist)
        percentText = findViewById(R.id.textChecklistPercent)
        emptyText = findViewById(R.id.textEmptyChecklist)
        fab = findViewById(R.id.fabAddTask)

        findViewById<Toolbar>(R.id.toolbar).setNavigationOnClickListener { finish() }
        updateHeaderTitle()

        adapter = ChecklistAdapter(
            onToggleComplete = ::toggleCompletion,
            onWeeklyDayToggled = ::toggleWeeklyDay,
            onEdit = ::showEditDialog,
            onDelete = ::showDeleteTaskConfirmation,
            onSyncCalendar = ::exportToSystemCalendar
        )
        recycler.layoutManager = LinearLayoutManager(this)
        recycler.adapter = adapter

        fab.setOnClickListener {
            startActivity(
                Intent(this, AddTaskActivity::class.java)
                    .putExtra(AddTaskActivity.EXTRA_SELECTED_PET_ID, selectedPetId ?: petId)
            )
        }
        // Shrink the FAB while scrolling so it doesn't cover the last row's actions.
        recycler.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                if (dy > 0) fab.hide() else if (dy < 0) fab.show()
            }
        })

        findViewById<TextInputEditText>(R.id.editChecklistSearch).addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                searchQuery = s?.toString()?.trim().orEmpty()
                mainHandler.removeCallbacks(searchRunnable)
                mainHandler.postDelayed(searchRunnable, SEARCH_DEBOUNCE_MS)
            }
        })

        chipGroup.setOnCheckedStateChangeListener { group, checkedIds ->
            val chip = checkedIds.firstOrNull()?.let { group.findViewById<Chip>(it) } ?: return@setOnCheckedStateChangeListener
            selectedPetId = chip.tag as? Long
            updateHeaderTitle()
            applyFilters()
        }
    }

    override fun onResume() {
        super.onResume()
        loadData()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putLong(STATE_PET_ID, selectedPetId ?: -1L)
    }

    override fun onDestroy() {
        mainHandler.removeCallbacks(searchRunnable)
        executor.shutdownNow()
        super.onDestroy()
    }

    // region Data

    /** Loads pets and all of today's tasks in the background, then renders. */
    private fun loadData() {
        runInBackground({
            val loadedPets = database.getPetOptions()
            val loadedTasks = database.getCareTasks(null)
            loadedPets to loadedTasks
        }) { (loadedPets, loadedTasks) ->
            if (loadedPets != pets) {
                pets = loadedPets
                if (selectedPetId != null && pets.none { it.id == selectedPetId }) selectedPetId = null
                rebuildPetChips()
            }
            allTasks = loadedTasks
            updateHeaderTitle()
            applyFilters()
        }
    }

    private fun applyFilters() {
        val petFiltered = selectedPetId?.let { id -> allTasks.filter { it.petId == id } } ?: allTasks
        val visible = if (searchQuery.isBlank()) petFiltered else petFiltered.filter { it.matches(searchQuery) }
        val sorted = visible.sortedWith(
            compareBy<CareTask> { it.isCompleted }
                .thenBy { minutesOfDay(it.scheduledTime) ?: Int.MAX_VALUE }
                .thenBy { it.description.lowercase() }
        )

        adapter.showPetName = selectedPetId == null && pets.size > 1
        adapter.submitList(sorted)
        updateProgress(petFiltered)

        emptyText.text = when {
            petFiltered.isEmpty() -> "No routines yet.\nTap + to add one."
            else -> "No routines match \"$searchQuery\"."
        }
        emptyText.visibility = if (sorted.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun CareTask.matches(query: String): Boolean =
        description.contains(query, ignoreCase = true) ||
            category.contains(query, ignoreCase = true) ||
            petName.contains(query, ignoreCase = true) ||
            taskNotes.contains(query, ignoreCase = true) ||
            requiredSupplies.contains(query, ignoreCase = true)

    /** Replaces one task in memory and re-renders, so the UI responds instantly before the DB write. */
    private fun updateLocalTask(taskId: Long, transform: (CareTask) -> CareTask) {
        allTasks = allTasks.map { if (it.id == taskId) transform(it) else it }
        applyFilters()
    }

    // endregion

    // region Header

    private fun rebuildPetChips() {
        chipGroup.removeAllViews()
        addPetChip("All pets", null)
        pets.forEach { addPetChip(it.name, it.id) }
    }

    private fun addPetChip(label: String, chipPetId: Long?) {
        val chip = Chip(this).apply {
            id = View.generateViewId()
            tag = chipPetId
            text = label
            isCheckable = true
            isCheckedIconVisible = false
            chipMinHeight = 40 * resources.displayMetrics.density
            setTextColor(ContextCompat.getColorStateList(this@ChecklistActivity, R.color.chip_selectable_text))
            chipBackgroundColor = ContextCompat.getColorStateList(this@ChecklistActivity, R.color.chip_selectable_bg)
            chipStrokeColor = ContextCompat.getColorStateList(this@ChecklistActivity, R.color.chip_selectable_stroke)
            chipStrokeWidth = 1f
        }
        chipGroup.addView(chip)
        if (chipPetId == selectedPetId) chipGroup.check(chip.id)
    }

    private fun updateHeaderTitle() {
        titleText.text = when (val id = selectedPetId) {
            null -> "All pets checklist"
            else -> "${pets.firstOrNull { it.id == id }?.name ?: petName} checklist"
        }
    }

    private fun updateProgress(tasks: List<CareTask>) {
        val total = tasks.size
        val done = tasks.count { it.isCompleted }
        if (total == 0) {
            progressRow.visibility = View.GONE
            subtitleText.text = "Today's routines"
            return
        }
        val percent = (done * 100f / total).roundToInt()
        progressRow.visibility = View.VISIBLE
        progress.setProgressCompat(percent, true)
        percentText.text = "$percent%"
        subtitleText.text = if (done == total) "All $total done today" else "$done of $total done today"
    }

    // endregion

    // region Actions

    private fun toggleCompletion(task: CareTask, source: View) {
        val completed = !task.isCompleted
        if (completed) source.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
        updateLocalTask(task.id) { it.copy(isCompleted = completed) }

        runInBackground({ database.updateTaskCompletion(task.id, completed) }) { saved ->
            if (!saved) {
                updateLocalTask(task.id) { it.copy(isCompleted = task.isCompleted) }
                showMessage("Couldn't update routine. Please try again.")
                return@runInBackground
            }
            val name = task.description.ifBlank { "Routine" }
            showMessage(if (completed) "\"$name\" done" else "\"$name\" marked as not done") {
                setAction("Undo") { toggleCompletion(task.copy(isCompleted = completed), source) }
            }
        }
    }

    private fun toggleWeeklyDay(task: CareTask, day: String, checked: Boolean) {
        val current = allTasks.firstOrNull { it.id == task.id } ?: return
        val days = current.completedWeekDays.split(",").map { it.trim() }.filter { it.isNotEmpty() }.toMutableSet()
        if (checked) days.add(day) else days.remove(day)
        val serialized = days.joinToString(",")
        updateLocalTask(task.id) { it.copy(completedWeekDays = serialized) }

        runInBackground({ database.updateWeeklyDayCompletion(task.id, serialized) }) { saved ->
            if (!saved) {
                updateLocalTask(task.id) { it.copy(completedWeekDays = current.completedWeekDays) }
                showMessage("Couldn't update ${ChecklistAdapter.fullDayName(day)}.")
            }
        }
    }

    private fun showDeleteTaskConfirmation(task: CareTask) {
        MaterialAlertDialogBuilder(this)
            .setTitle("Delete routine?")
            .setMessage("\"${task.description.ifBlank { "This routine" }}\" and its history will be removed. This can't be undone.")
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Delete") { _, _ ->
                runInBackground({ database.deleteTask(task.id) }) { deleted ->
                    if (deleted) {
                        allTasks = allTasks.filterNot { it.id == task.id }
                        applyFilters()
                        showMessage("Routine deleted")
                    } else {
                        showMessage("Couldn't delete routine. Please try again.")
                    }
                }
            }
            .show()
    }

    private fun showEditDialog(task: CareTask) {
        if (task.isCompleted) return
        val form = LayoutInflater.from(this).inflate(R.layout.dialog_edit_task, null)
        val titleLayout = form.findViewById<TextInputLayout>(R.id.layoutEditTaskTitle)
        val titleInput = form.findViewById<TextInputEditText>(R.id.editTaskTitle)
        val timeInput = form.findViewById<TextInputEditText>(R.id.editTaskTime)
        val suppliesInput = form.findViewById<TextInputEditText>(R.id.editTaskSupplies)
        val notesInput = form.findViewById<TextInputEditText>(R.id.editTaskNotes)

        titleInput.setText(task.description)
        timeInput.setText(task.scheduledTime)
        suppliesInput.setText(task.requiredSupplies)
        notesInput.setText(task.taskNotes)
        timeInput.setOnClickListener { showTimePicker(timeInput) }

        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle("Edit routine")
            .setView(form)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Save", null)
            .show()

        // Set the listener after show() so an invalid form keeps the dialog open.
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val description = titleInput.text?.toString()?.trim().orEmpty()
            if (description.isBlank()) {
                titleLayout.error = "Title is required"
                return@setOnClickListener
            }
            val time = timeInput.text?.toString()?.trim().orEmpty()
            val supplies = suppliesInput.text?.toString()?.trim().orEmpty()
            val notes = notesInput.text?.toString()?.trim().orEmpty()
            dialog.dismiss()

            runInBackground({ database.updateTaskDetails(task.id, description, time, supplies, notes) }) { saved ->
                if (saved) {
                    updateLocalTask(task.id) {
                        it.copy(description = description, scheduledTime = time, requiredSupplies = supplies, taskNotes = notes)
                    }
                    showMessage("Changes saved")
                } else {
                    showMessage("Couldn't save changes. Please try again.")
                }
            }
        }
        titleInput.setOnFocusChangeListener { _, _ -> titleLayout.error = null }
    }

    private fun showTimePicker(input: TextInputEditText) {
        val initialMinutes = minutesOfDay(input.text?.toString().orEmpty())
        val now = Calendar.getInstance()
        TimePickerDialog(
            this,
            { _, hourOfDay, minute ->
                val suffix = if (hourOfDay >= 12) "PM" else "AM"
                val hour = when {
                    hourOfDay == 0 -> 12
                    hourOfDay > 12 -> hourOfDay - 12
                    else -> hourOfDay
                }
                input.setText(String.format(Locale.US, "%02d:%02d %s", hour, minute, suffix))
            },
            initialMinutes?.div(60) ?: now.get(Calendar.HOUR_OF_DAY),
            initialMinutes?.rem(60) ?: now.get(Calendar.MINUTE),
            false
        ).show()
    }

    private fun exportToSystemCalendar(task: CareTask) {
        // Start at the routine's scheduled time (tomorrow if it has already passed today), else in an hour.
        val start = Calendar.getInstance().apply {
            val minutes = minutesOfDay(task.scheduledTime)
            if (minutes == null) {
                add(Calendar.HOUR_OF_DAY, 1)
            } else {
                set(Calendar.HOUR_OF_DAY, minutes / 60)
                set(Calendar.MINUTE, minutes % 60)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
                if (before(Calendar.getInstance())) add(Calendar.DAY_OF_MONTH, 1)
            }
        }
        val description = buildString {
            append("Routine care task for ${task.petName}.")
            if (task.requiredSupplies.isNotBlank()) append("\nSupplies: ${task.requiredSupplies}")
            if (task.taskNotes.isNotBlank()) append("\nNotes: ${task.taskNotes}")
        }
        val intent = Intent(Intent.ACTION_INSERT)
            .setData(CalendarContract.Events.CONTENT_URI)
            .putExtra(CalendarContract.Events.TITLE, "PetCare: ${task.description} (${task.petName})")
            .putExtra(CalendarContract.Events.DESCRIPTION, description)
            .putExtra(CalendarContract.Events.EVENT_LOCATION, task.locationName.orEmpty())
            .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, start.timeInMillis)
            .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, start.timeInMillis + 30 * 60 * 1000)

        try {
            startActivity(intent)
        } catch (_: Exception) {
            Toast.makeText(this, "No calendar app found", Toast.LENGTH_SHORT).show()
        }
    }

    // endregion

    // region Helpers

    /** Runs [work] off the main thread and delivers its result to [onResult] if the screen is still alive. */
    private fun <T> runInBackground(work: () -> T, onResult: (T) -> Unit) {
        if (executor.isShutdown) return
        executor.execute {
            val result = work()
            runOnUiThread { if (!isFinishing && !isDestroyed) onResult(result) }
        }
    }

    private fun showMessage(message: String, configure: Snackbar.() -> Unit = {}) {
        Snackbar.make(recycler, message, Snackbar.LENGTH_LONG)
            .setAnchorView(fab.takeIf { it.isShown })
            .apply(configure)
            .show()
    }

    /** Parses stored times like "08:30 AM" into minutes after midnight. */
    private fun minutesOfDay(time: String): Int? {
        if (time.isBlank()) return null
        return try {
            val parsed = timeFormat.parse(time.trim()) ?: return null
            val calendar = Calendar.getInstance().apply { this.time = parsed }
            calendar.get(Calendar.HOUR_OF_DAY) * 60 + calendar.get(Calendar.MINUTE)
        } catch (_: Exception) {
            null
        }
    }

    private val timeFormat = SimpleDateFormat("hh:mm a", Locale.US)

    private fun updateStatusBarIcons() {
        val isDarkMode = (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES
        WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightStatusBars = !isDarkMode
    }

    // endregion

    companion object {
        const val EXTRA_PET_ID = "extra_pet_id"
        const val EXTRA_PET_NAME = "extra_pet_name"
        private const val STATE_PET_ID = "state_pet_id"
        private const val SEARCH_DEBOUNCE_MS = 250L
    }
}
