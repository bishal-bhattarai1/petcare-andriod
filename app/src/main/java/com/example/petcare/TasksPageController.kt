package com.example.petcare

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.imageview.ShapeableImageView
import com.google.android.material.progressindicator.LinearProgressIndicator
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * Drives the Care Tasks page (layout `activity_tasks`). Shared by [TasksActivity] and the
 * Tasks tab embedded in [DashboardActivity] so both behave identically.
 */
class TasksPageController(
    private val activity: AppCompatActivity,
    private val page: View,
    private val database: AuthDatabaseHelper,
    private val sessionManager: SessionManager
) {
    private var taskAdapter: TaskAdapter? = null
    private var selectedPetId: Long? = null
    private var selectedCategory: String = "All"
    private val selectedCalendar: Calendar = Calendar.getInstance()

    private val categoryChips = listOf(
        Triple(R.id.chipFilterAll, "All", emptyList()),
        Triple(R.id.chipFilterFeeding, "Feeding", listOf("Feed", "Food")),
        Triple(R.id.chipFilterGrooming, "Grooming", listOf("Groom")),
        Triple(R.id.chipFilterHealth, "Health", listOf("Health", "Med", "Vet")),
        Triple(R.id.chipFilterWalking, "Activity", listOf("Activity", "Exercise", "Walk"))
    )

    fun setup() {
        setupHeaderAndDate()
        setupCategoryFilterChips()
        setupTaskList()
        page.findViewById<View>(R.id.buttonAddRoutineTask).setOnClickListener {
            activity.startActivity(Intent(activity, AddTaskActivity::class.java))
        }
    }

    /** Reloads everything that may have changed while the page was hidden. */
    fun refresh() {
        updateDateText()
        updateHeaderAvatar()
        setupPetFilterChips()
        render()
    }

    private fun setupHeaderAndDate() {
        page.findViewById<View>(R.id.textCurrentDate).setOnClickListener {
            DatePickerDialog(activity, { _, y, m, d ->
                selectedCalendar.set(y, m, d)
                updateDateText()
                render()
            },
                selectedCalendar.get(Calendar.YEAR),
                selectedCalendar.get(Calendar.MONTH),
                selectedCalendar.get(Calendar.DAY_OF_MONTH)
            ).show()
        }
        page.findViewById<View>(R.id.buttonNotification).setOnClickListener {
            activity.startActivity(Intent(activity, NotificationsActivity::class.java))
        }
        updateDateText()
    }

    private fun selectedDateKey(): String = AuthDatabaseHelper.dateKey(selectedCalendar)

    private fun isSelectedToday(): Boolean = selectedDateKey() == AuthDatabaseHelper.todayKey()

    private fun isSelectedInFuture(): Boolean = selectedDateKey() > AuthDatabaseHelper.todayKey()

    private fun updateDateText() {
        val formattedDate = SimpleDateFormat("MMM d", Locale.getDefault()).format(selectedCalendar.time)
        page.findViewById<TextView>(R.id.textCurrentDate).text =
            if (isSelectedToday()) "Today" else formattedDate
        page.findViewById<TextView>(R.id.textHeaderSubtitle).text =
            SimpleDateFormat("EEEE, MMM d", Locale.getDefault()).format(selectedCalendar.time)
        page.findViewById<TextView>(R.id.textProgressTitle).text =
            if (isSelectedToday()) "TODAY'S PROGRESS" else "PROGRESS · ${formattedDate.uppercase(Locale.getDefault())}"
    }

    private fun updateHeaderAvatar() {
        val avatar = page.findViewById<ShapeableImageView>(R.id.imgHeaderAvatar)
        val imageUriStr = sessionManager.getProfileImageUri()
        // Only show the photo uploaded in profile settings; no placeholder icon otherwise.
        if (!imageUriStr.isNullOrBlank()) {
            try {
                avatar.setImageURI(Uri.parse(imageUriStr))
            } catch (_: Exception) {
                avatar.setImageDrawable(null)
            }
        } else {
            avatar.setImageDrawable(null)
        }
        avatar.visibility = if (avatar.drawable != null) View.VISIBLE else View.GONE
    }

    private fun setupPetFilterChips() {
        val chipGroupPets = page.findViewById<ChipGroup>(R.id.chipGroupPets)
        chipGroupPets.removeAllViews()

        val pets = database.getPetOptions()
        if (pets.none { it.id == selectedPetId }) selectedPetId = null

        addPetChip(chipGroupPets, "All pets", null, selectedPetId == null)
        pets.forEach { pet ->
            addPetChip(chipGroupPets, pet.name, pet.id, selectedPetId == pet.id)
        }
    }

    private fun addPetChip(chipGroup: ChipGroup, label: String, petId: Long?, checked: Boolean) {
        val chip = Chip(activity).apply {
            id = View.generateViewId()
            text = label
            isCheckable = true
            isChecked = checked
            chipMinHeight = 40 * resources.displayMetrics.density
            setTextColor(ContextCompat.getColorStateList(activity, R.color.chip_selectable_text))
            chipBackgroundColor = ContextCompat.getColorStateList(activity, R.color.chip_selectable_bg)
            chipStrokeColor = ContextCompat.getColorStateList(activity, R.color.chip_selectable_stroke)
            chipStrokeWidth = 1f
            checkedIcon = null
            isCheckedIconVisible = false
            setOnClickListener {
                selectedPetId = petId
                setupPetFilterChips()
                render()
            }
        }
        chipGroup.addView(chip)
    }

    private fun styleChip(chip: Chip, isChecked: Boolean) {
        if (isChecked) {
            chip.chipBackgroundColor = ColorStateList.valueOf(ContextCompat.getColor(activity, R.color.md_secondary_container))
            chip.setTextColor(ContextCompat.getColor(activity, R.color.md_on_secondary_container))
        } else {
            chip.chipBackgroundColor = ColorStateList.valueOf(ContextCompat.getColor(activity, R.color.md_background))
            chip.setTextColor(ContextCompat.getColor(activity, R.color.md_on_surface_variant))
        }
    }

    private fun setupCategoryFilterChips() {
        val chipGroup = page.findViewById<ChipGroup>(R.id.chipGroupTaskFilters)

        // Keep one filter selected at all times, and style the initial selection.
        chipGroup.isSelectionRequired = true
        categoryChips.forEach { (chipId, _, _) ->
            page.findViewById<Chip>(chipId)?.let { styleChip(it, it.isChecked) }
        }

        chipGroup.setOnCheckedStateChangeListener { _, checkedIds ->
            categoryChips.forEach { (chipId, category, _) ->
                val chip = page.findViewById<Chip>(chipId) ?: return@forEach
                val isChecked = checkedIds.contains(chipId)
                styleChip(chip, isChecked)
                if (isChecked) selectedCategory = category
            }
            render()
        }
    }

    private fun matchesCategory(task: CareTask, keywords: List<String>): Boolean =
        keywords.isEmpty() || keywords.any { task.category.contains(it, ignoreCase = true) }

    /** Weekly routines only appear on their chosen weekdays; everything else is daily. */
    private fun isScheduledOn(task: CareTask, calendar: Calendar): Boolean {
        if (!task.repeatType.equals("Weekly", ignoreCase = true)) return true
        val days = task.weekDays.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        if (days.isEmpty()) return true
        val dayCode = SimpleDateFormat("EEE", Locale.US).format(calendar.time)
        return days.any { it.equals(dayCode, ignoreCase = true) }
    }

    fun render() {
        val petTasks = database.getCareTasks(selectedPetId, selectedDateKey())
            .filter { isScheduledOn(it, selectedCalendar) }

        // Progress for the selected day across all categories
        val totalCount = petTasks.size
        val completedCount = petTasks.count { it.isCompleted }
        val remainingCount = totalCount - completedCount
        val percentage = if (totalCount > 0) (completedCount * 100) / totalCount else 0

        page.findViewById<TextView>(R.id.textProgressCount).text =
            if (totalCount == 0) "No tasks yet" else "$completedCount of $totalCount completed"
        page.findViewById<LinearProgressIndicator>(R.id.progressTasksOverview).setProgressCompat(percentage, true)
        page.findViewById<TextView>(R.id.textTasksOverviewRatio).text = "$percentage%"

        page.findViewById<TextView>(R.id.textProgressHint).text = when {
            totalCount == 0 -> "Tap + to add a routine for your pet."
            remainingCount == 0 -> "All done for the day. Great job!"
            else -> {
                val left = if (remainingCount == 1) "1 task left" else "$remainingCount tasks left"
                val next = petTasks.firstOrNull { !it.isCompleted }?.description?.takeIf { it.isNotBlank() }
                if (next != null) "$left · Next: $next" else left
            }
        }

        // Red dot on the bell while today's routines are still pending
        page.findViewById<View>(R.id.viewNotificationBadge).visibility =
            if (isSelectedToday() && remainingCount > 0) View.VISIBLE else View.GONE

        // Category chips show how many tasks each one holds for the day
        categoryChips.forEach { (chipId, label, keywords) ->
            val count = petTasks.count { matchesCategory(it, keywords) }
            page.findViewById<Chip>(chipId)?.text = if (count > 0) "$label · $count" else label
        }

        val keywords = categoryChips.firstOrNull { it.second == selectedCategory }?.third.orEmpty()
        val displayedTasks = petTasks.filter { matchesCategory(it, keywords) }
        taskAdapter?.updateTasks(displayedTasks)

        page.findViewById<TextView>(R.id.textEmptyTasks).apply {
            text = if (totalCount == 0) "No tasks for this day." else "No $selectedCategory tasks for this day."
            visibility = if (displayedTasks.isEmpty()) View.VISIBLE else View.GONE
        }
    }

    /** Marks a routine done for the selected day. Done is final and time-gated ([CompletionRules]). */
    private fun completeTask(task: CareTask) {
        val reason = CompletionRules.blockReason(task, selectedDateKey())
        val message = when {
            reason != null -> reason
            database.updateTaskCompletion(task.id, true, selectedDateKey()) -> "${task.description.ifBlank { "Routine" }} completed"
            else -> "Could not update task"
        }
        Toast.makeText(activity, message, Toast.LENGTH_SHORT).show()
        render()
    }

    private fun confirmDelete(task: CareTask) {
        MaterialAlertDialogBuilder(activity)
            .setTitle("Delete Routine?")
            .setMessage("Remove \"${task.description}\" permanently?")
            .setPositiveButton("Delete") { _, _ ->
                database.deleteTask(task.id)
                render()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun openChecklist(task: CareTask) {
        activity.startActivity(
            Intent(activity, ChecklistActivity::class.java)
                .putExtra(ChecklistActivity.EXTRA_PET_ID, task.petId)
                .putExtra(ChecklistActivity.EXTRA_PET_NAME, task.petName)
        )
    }

    private fun setupTaskList() {
        val rv = page.findViewById<RecyclerView>(R.id.recyclerViewTasks)
        val adapter = TaskAdapter(
            emptyList(),
            onComplete = ::completeTask,
            onDelete = ::confirmDelete,
            onClick = ::openChecklist,
            onEdit = ::showEditTaskDialog
        )
        taskAdapter = adapter
        rv.layoutManager = LinearLayoutManager(activity)
        rv.adapter = adapter

        // Swipe right to toggle completion, swipe left to delete
        val swipeHandler = object : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT) {
            override fun onMove(r: RecyclerView, v: RecyclerView.ViewHolder, t: RecyclerView.ViewHolder) = false

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val position = viewHolder.absoluteAdapterPosition
                val task = adapter.tasks.getOrNull(position) ?: return
                // Snap the row back; render() reflects the real outcome.
                adapter.notifyItemChanged(position)
                if (direction == ItemTouchHelper.RIGHT) completeTask(task) else confirmDelete(task)
            }

            override fun onChildDraw(
                c: Canvas, rv: RecyclerView, vh: RecyclerView.ViewHolder,
                dX: Float, dY: Float, actionState: Int, isCurrentlyActive: Boolean
            ) {
                val itemView = vh.itemView
                val itemHeight = itemView.bottom - itemView.top

                if (dX > 0) {
                    val background = ColorDrawable(ContextCompat.getColor(activity, R.color.md_primary))
                    background.setBounds(itemView.left, itemView.top, itemView.left + dX.toInt(), itemView.bottom)
                    background.draw(c)

                    ContextCompat.getDrawable(activity, R.drawable.ic_status_check)?.let {
                        val iconMargin = (itemHeight - it.intrinsicHeight) / 2
                        val iconTop = itemView.top + iconMargin
                        val iconLeft = itemView.left + iconMargin
                        it.setBounds(iconLeft, iconTop, iconLeft + it.intrinsicWidth, iconTop + it.intrinsicHeight)
                        it.setTint(ContextCompat.getColor(activity, R.color.md_on_primary))
                        it.draw(c)
                    }
                } else if (dX < 0) {
                    val background = ColorDrawable(ContextCompat.getColor(activity, R.color.md_error))
                    background.setBounds(itemView.right + dX.toInt(), itemView.top, itemView.right, itemView.bottom)
                    background.draw(c)
                }
                super.onChildDraw(c, rv, vh, dX, dY, actionState, isCurrentlyActive)
            }
        }
        ItemTouchHelper(swipeHandler).attachToRecyclerView(rv)
    }

    private fun showEditTaskDialog(task: CareTask) {
        // Completed tasks are locked; reopen the task first to edit it.
        if (task.isCompleted) return
        val density = activity.resources.displayMetrics.density
        val form = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((24 * density).toInt(), (16 * density).toInt(), (24 * density).toInt(), 0)
        }

        val descriptionInput = EditText(activity).apply {
            hint = "Task title"
            setText(task.description)
            setSingleLine(true)
        }
        val timeInput = EditText(activity).apply {
            hint = "Time"
            setText(task.scheduledTime)
            isFocusable = false
            setOnClickListener { showTimePicker(this) }
        }
        val suppliesInput = EditText(activity).apply {
            hint = "Required supplies"
            setText(task.requiredSupplies)
        }
        val notesInput = EditText(activity).apply {
            hint = "Special instructions"
            setText(task.taskNotes)
            minLines = 2
        }

        val labelPadding = (4 * density).toInt()
        val sectionPadding = (16 * density).toInt()
        form.addView(TextView(activity).apply { text = "Title & Time"; textSize = 12f; setPadding(0, 0, 0, labelPadding) })
        form.addView(descriptionInput)
        form.addView(timeInput)
        form.addView(TextView(activity).apply { text = "Resources & Notes"; textSize = 12f; setPadding(0, sectionPadding, 0, labelPadding) })
        form.addView(suppliesInput)
        form.addView(notesInput)

        MaterialAlertDialogBuilder(activity)
            .setTitle("Edit Care Routine")
            .setView(form)
            .setPositiveButton("Save Changes") { _, _ ->
                val description = descriptionInput.text.toString().trim()
                if (description.isBlank()) {
                    Toast.makeText(activity, "Task title is required", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                val saved = database.updateTaskDetails(
                    task.id,
                    description,
                    timeInput.text.toString().trim(),
                    suppliesInput.text.toString().trim(),
                    notesInput.text.toString().trim()
                )
                Toast.makeText(activity, if (saved) "Changes saved" else "Could not save task", Toast.LENGTH_SHORT).show()
                render()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showTimePicker(input: EditText) {
        val calendar = Calendar.getInstance()
        TimePickerDialog(
            activity,
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
}
