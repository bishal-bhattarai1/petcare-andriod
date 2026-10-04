package com.example.petcare

import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.widget.PopupMenu
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup

/** Checklist rows for [ChecklistActivity]. Actions live in an overflow menu so rows never overflow. */
class ChecklistAdapter(
    private val onToggleComplete: (CareTask, View) -> Unit,
    private val onWeekDayTapped: (CareTask, String, View) -> Unit,
    private val onEdit: (CareTask) -> Unit,
    private val onDelete: (CareTask) -> Unit,
    private val onSyncCalendar: (CareTask) -> Unit
) : ListAdapter<CareTask, ChecklistAdapter.ViewHolder>(DIFF) {

    /** Show the pet name in each row when the list mixes several pets. */
    var showPetName: Boolean = false
        set(value) {
            if (field != value) {
                field = value
                notifyItemRangeChanged(0, itemCount)
            }
        }

    init {
        setHasStableIds(true)
    }

    override fun getItemId(position: Int): Long = getItem(position).id

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder =
        ViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.item_checklist_task, parent, false))

    override fun onBindViewHolder(holder: ViewHolder, position: Int) = holder.bind(getItem(position))

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val card: MaterialCardView = view.findViewById(R.id.checklistCard)
        private val toggle: FrameLayout = view.findViewById(R.id.checklistToggle)
        private val toggleIcon: ImageView = view.findViewById(R.id.checklistToggleIcon)
        private val title: TextView = view.findViewById(R.id.checklistTitle)
        private val meta: TextView = view.findViewById(R.id.checklistMeta)
        private val category: TextView = view.findViewById(R.id.checklistCategory)
        private val more: ImageButton = view.findViewById(R.id.checklistMore)
        private val details: TextView = view.findViewById(R.id.checklistDetails)
        private val weeklySection: LinearLayout = view.findViewById(R.id.checklistWeeklySection)
        private val weeklyLabel: TextView = view.findViewById(R.id.checklistWeeklyLabel)
        private val weeklyDays: ChipGroup = view.findViewById(R.id.checklistWeeklyDays)

        private val ctx = view.context
        private val density = ctx.resources.displayMetrics.density
        private val colorPrimaryText = ContextCompat.getColor(ctx, R.color.app_text_primary)
        private val colorSecondaryText = ContextCompat.getColor(ctx, R.color.app_text_secondary)
        private val colorDone = ContextCompat.getColor(ctx, R.color.status_green)
        private val fontRegular = ctx.figtree()
        private val fontBold = ctx.figtree(Typeface.BOLD)
        private val openCircle = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setStroke((2 * density).toInt(), ContextCompat.getColor(ctx, R.color.md_outline))
            setColor(Color.TRANSPARENT)
        }
        private val doneCircle = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(colorDone)
        }

        fun bind(task: CareTask) {
            val done = task.isCompleted
            val taskTitle = task.description.ifBlank { "Care task" }

            title.text = taskTitle
            title.typeface = if (done) fontRegular else fontBold
            title.setTextColor(if (done) colorSecondaryText else colorPrimaryText)
            title.paintFlags = if (done) {
                title.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
            } else {
                title.paintFlags and Paint.STRIKE_THRU_TEXT_FLAG.inv()
            }

            meta.text = listOfNotNull(
                task.scheduledTime.ifBlank { "Anytime" },
                TaskSchedule.label(task),
                task.petName.takeIf { showPetName && it.isNotBlank() }
            ).joinToString(" · ")

            // Completion toggle: filled circle with a check when done, outline when open.
            toggleIcon.background = if (done) doneCircle else openCircle
            toggleIcon.setImageResource(if (done) R.drawable.ic_status_check else 0)
            toggleIcon.imageTintList = ColorStateList.valueOf(ContextCompat.getColor(ctx, R.color.md_on_primary))
            // Done is final; before its time the circle is dimmed and explains why on tap.
            val timeReached = CompletionRules.isTimeReached(task.scheduledTime)
            toggle.alpha = if (done || timeReached) 1f else 0.4f
            toggle.contentDescription = when {
                done -> "$taskTitle is done"
                timeReached -> "Mark $taskTitle as done"
                else -> "$taskTitle, available from ${task.scheduledTime}"
            }
            if (done) {
                toggle.setOnClickListener(null)
                toggle.isClickable = false
            } else {
                toggle.setOnClickListener { onToggleComplete(task, it) }
            }

            val (categoryBg, categoryFg) = if (done) {
                R.color.md_surface_container_high to R.color.md_on_surface_variant
            } else {
                taskCategoryColors(task.category)
            }
            category.text = if (done) "Done" else task.category.ifBlank { "Care" }
            category.backgroundTintList = ContextCompat.getColorStateList(ctx, categoryBg)
            category.setTextColor(ContextCompat.getColor(ctx, categoryFg))

            val detailText = listOfNotNull(
                task.requiredSupplies.takeIf { it.isNotBlank() }?.let { "Supplies: $it" },
                task.taskNotes.takeIf { it.isNotBlank() }
            ).joinToString("\n")
            details.text = detailText
            details.visibility = if (detailText.isNotEmpty() && !done) View.VISIBLE else View.GONE

            bindWeekly(task)

            card.alpha = if (done) 0.7f else 1f
            // Completed routines are locked: no edit until they're marked as not done.
            if (done) {
                card.setOnClickListener(null)
                card.isClickable = false
            } else {
                card.setOnClickListener { onEdit(task) }
            }
            more.setOnClickListener { showMenu(it, task) }
            card.setOnLongClickListener {
                it.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
                showMenu(more, task)
                true
            }
        }

        /**
         * Read-only "this week" strip for weekly routines. Chips never toggle themselves: only
         * today's chip completes the routine, so past days can't be backfilled all at once.
         */
        private fun bindWeekly(task: CareTask) {
            val days = WEEK_ORDER.filter { day -> task.weekDays.split(",").any { it.trim() == day } }
            if (!task.repeatType.equals("Weekly", ignoreCase = true) || days.isEmpty()) {
                weeklySection.visibility = View.GONE
                weeklyDays.removeAllViews()
                return
            }

            val statuses = days.associateWith { CompletionRules.weekDayStatus(it, task) }
            val doneCount = statuses.values.count { it == CompletionRules.WeekDayStatus.DONE }
            weeklySection.visibility = View.VISIBLE
            weeklyLabel.text = "This week · $doneCount of ${days.size} days done"

            weeklyDays.removeAllViews()
            statuses.forEach { (day, status) ->
                weeklyDays.addView(Chip(ctx).apply {
                    text = day
                    isCheckable = false
                    chipMinHeight = 32 * density
                    ensureAccessibleTouchTarget((40 * density).toInt())
                    chipStrokeWidth = density
                    styleWeekDay(this, status)
                    contentDescription = "${fullDayName(day)}, ${status.name.lowercase()}"
                    setOnClickListener { onWeekDayTapped(task, day, it) }
                })
            }
        }

        private fun styleWeekDay(chip: Chip, status: CompletionRules.WeekDayStatus) {
            val (bg, stroke, fg) = when (status) {
                CompletionRules.WeekDayStatus.DONE -> Triple(R.color.md_primary, R.color.md_primary, R.color.md_on_primary)
                CompletionRules.WeekDayStatus.TODAY -> Triple(R.color.md_secondary_container, R.color.md_secondary, R.color.md_on_secondary_container)
                CompletionRules.WeekDayStatus.MISSED -> Triple(R.color.md_error_container, R.color.md_error_container, R.color.md_on_error_container)
                CompletionRules.WeekDayStatus.UPCOMING -> Triple(R.color.app_input_bg, R.color.app_divider, R.color.app_text_secondary)
            }
            chip.chipBackgroundColor = ContextCompat.getColorStateList(ctx, bg)
            chip.chipStrokeColor = ContextCompat.getColorStateList(ctx, stroke)
            chip.setTextColor(ContextCompat.getColor(ctx, fg))
            chip.typeface = if (status == CompletionRules.WeekDayStatus.TODAY) fontBold else fontRegular
            chip.alpha = if (status == CompletionRules.WeekDayStatus.UPCOMING) 0.6f else 1f
            chip.isChipIconVisible = status == CompletionRules.WeekDayStatus.DONE
            if (status == CompletionRules.WeekDayStatus.DONE) {
                chip.setChipIconResource(R.drawable.ic_status_check)
                chip.chipIconTint = ContextCompat.getColorStateList(ctx, R.color.md_on_primary)
                chip.chipIconSize = 14 * density
            }
            chip.paintFlags = if (status == CompletionRules.WeekDayStatus.MISSED) {
                chip.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
            } else {
                chip.paintFlags and Paint.STRIKE_THRU_TEXT_FLAG.inv()
            }
        }

        private fun showMenu(anchor: View, task: CareTask) {
            PopupMenu(ctx, anchor).apply {
                if (!task.isCompleted) menu.add(0, MENU_TOGGLE, 0, "Mark as done")
                if (!task.isCompleted) menu.add(0, MENU_EDIT, 1, "Edit routine")
                menu.add(0, MENU_CALENDAR, 2, "Add to calendar")
                menu.add(0, MENU_DELETE, 3, "Delete")
                setOnMenuItemClickListener { item ->
                    when (item.itemId) {
                        MENU_TOGGLE -> onToggleComplete(task, anchor)
                        MENU_EDIT -> onEdit(task)
                        MENU_CALENDAR -> onSyncCalendar(task)
                        MENU_DELETE -> onDelete(task)
                        else -> return@setOnMenuItemClickListener false
                    }
                    true
                }
            }.show()
        }
    }

    companion object {
        private const val MENU_TOGGLE = 1
        private const val MENU_EDIT = 2
        private const val MENU_CALENDAR = 3
        private const val MENU_DELETE = 4

        private val WEEK_ORDER = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")

        private val DIFF = object : DiffUtil.ItemCallback<CareTask>() {
            override fun areItemsTheSame(oldItem: CareTask, newItem: CareTask) = oldItem.id == newItem.id
            override fun areContentsTheSame(oldItem: CareTask, newItem: CareTask) = oldItem == newItem
        }

        fun fullDayName(day: String): String = when (day) {
            "Mon" -> "Monday"
            "Tue" -> "Tuesday"
            "Wed" -> "Wednesday"
            "Thu" -> "Thursday"
            "Fri" -> "Friday"
            "Sat" -> "Saturday"
            "Sun" -> "Sunday"
            else -> day
        }
    }
}
