package com.example.petcare

import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.widget.PopupMenu
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView

class TaskAdapter(
    var tasks: List<CareTask>,
    private val onComplete: (CareTask) -> Unit,
    private val onDelete: (CareTask) -> Unit,
    private val onClick: (CareTask) -> Unit,
    private val onEdit: ((CareTask) -> Unit)? = null
) : RecyclerView.Adapter<TaskAdapter.TaskViewHolder>() {

    class TaskViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val card: com.google.android.material.card.MaterialCardView = view.findViewById(R.id.taskCard)
        val accentBar: View = view.findViewById(R.id.taskAccentBar)
        val checkbox: FrameLayout = view.findViewById(R.id.taskCheckbox)
        val checkImg: ImageView = view.findViewById(R.id.taskCheckImg)
        val description: TextView = view.findViewById(R.id.taskDesc)
        val petBadge: TextView = view.findViewById(R.id.taskPetBadge)
        val schedule: TextView = view.findViewById(R.id.taskSchedule)
        val badge: TextView = view.findViewById(R.id.taskBadge)
        val moreOptions: ImageView = view.findViewById(R.id.taskMoreOptions)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TaskViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_task_row, parent, false)
        return TaskViewHolder(view)
    }

    override fun onBindViewHolder(holder: TaskViewHolder, position: Int) {
        val task = tasks[position]
        val ctx = holder.itemView.context

        holder.description.text = task.description.ifBlank { "Care task" }

        // Bind Pet Name Badge
        if (task.petName.isNotBlank()) {
            holder.petBadge.visibility = View.VISIBLE
            holder.petBadge.text = task.petName
        } else {
            holder.petBadge.visibility = View.GONE
        }

        val scheduleText = buildString {
            if (task.scheduledTime.isNotBlank()) {
                append(task.scheduledTime)
            } else {
                append("Anytime")
            }
            if (task.requiredSupplies.isNotBlank()) {
                append(" • ").append(task.requiredSupplies)
            } else if (task.taskNotes.isNotBlank()) {
                append(" • ").append(task.taskNotes)
            } else if (task.repeatType.isNotBlank()) {
                append(" • ").append(task.repeatType)
            }
        }
        holder.schedule.text = scheduleText

        // Accent bar + badge use the palette's task-category colours
        val (categoryBg, categoryFg) = taskCategoryColors(task.category)
        holder.badge.setBackgroundResource(R.drawable.bg_task_badge_done)
        if (task.isCompleted) {
            holder.accentBar.setBackgroundColor(ContextCompat.getColor(ctx, R.color.md_outline_variant))
            holder.badge.backgroundTintList = ContextCompat.getColorStateList(ctx, R.color.md_surface_container_high)
            holder.badge.text = "Done"
            holder.badge.setTextColor(ContextCompat.getColor(ctx, R.color.md_on_surface_variant))
        } else {
            holder.accentBar.setBackgroundColor(ContextCompat.getColor(ctx, categoryFg))
            holder.badge.backgroundTintList = ContextCompat.getColorStateList(ctx, categoryBg)
            holder.badge.text = task.category.ifBlank { "Care" }
            holder.badge.setTextColor(ContextCompat.getColor(ctx, categoryFg))
        }

        // Completion visual state
        if (task.isCompleted) {
            holder.card.alpha = 0.75f
            holder.description.paintFlags = holder.description.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
            holder.description.setTextColor(ContextCompat.getColor(ctx, R.color.auth_text_grey))
            holder.description.typeface = ctx.figtree()
            holder.checkImg.setImageResource(R.drawable.ic_status_check)
            holder.checkImg.imageTintList = android.content.res.ColorStateList.valueOf(ContextCompat.getColor(ctx, R.color.auth_teal_primary))
        } else {
            holder.card.alpha = 1.0f
            holder.description.paintFlags = holder.description.paintFlags and Paint.STRIKE_THRU_TEXT_FLAG.inv()
            holder.description.setTextColor(ContextCompat.getColor(ctx, R.color.auth_text_navy))
            holder.description.typeface = ctx.figtree(Typeface.BOLD)

            val outlineCircle = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setStroke((1.5 * ctx.resources.displayMetrics.density).toInt(), ContextCompat.getColor(ctx, R.color.auth_text_grey))
                setColor(Color.TRANSPARENT)
            }
            holder.checkImg.setImageDrawable(outlineCircle)
            holder.checkImg.imageTintList = null
        }

        holder.card.setOnClickListener { onClick(task) }
        holder.checkbox.setOnClickListener { onComplete(task) }

        // Popup Menu on Three Dots
        holder.moreOptions.setOnClickListener { view ->
            val popup = PopupMenu(ctx, view)
            if (!task.isCompleted) popup.menu.add(0, 1, 0, "✓ Mark Complete")
            popup.menu.add(0, 2, 1, "📋 Open Checklist")
            if (onEdit != null && !task.isCompleted) {
                popup.menu.add(0, 4, 2, "✏️ Edit Routine")
            }
            popup.menu.add(0, 3, 3, "🗑️ Delete Routine")
            popup.setOnMenuItemClickListener { menuItem ->
                when (menuItem.itemId) {
                    1 -> {
                        onComplete(task)
                        true
                    }
                    2 -> {
                        onClick(task)
                        true
                    }
                    3 -> {
                        onDelete(task)
                        true
                    }
                    4 -> {
                        onEdit?.invoke(task)
                        true
                    }
                    else -> false
                }
            }
            popup.show()
        }
    }

    override fun getItemCount() = tasks.size

    fun updateTasks(newTasks: List<CareTask>) {
        this.tasks = newTasks
        notifyDataSetChanged()
    }
}

/** Background / foreground colour resources for a task category (see PetCare palette §3). */
fun taskCategoryColors(category: String): Pair<Int, Int> {
    val c = category.lowercase()
    return when {
        c.contains("feed") || c.contains("food") -> R.color.cat_feeding_bg to R.color.cat_feeding_fg
        c.contains("exercise") || c.contains("walk") || c.contains("activity") -> R.color.cat_exercise_bg to R.color.cat_exercise_fg
        c.contains("groom") -> R.color.cat_grooming_bg to R.color.cat_grooming_fg
        c.contains("med") -> R.color.cat_medication_bg to R.color.cat_medication_fg
        c.contains("health") || c.contains("vet") -> R.color.cat_healthcare_bg to R.color.cat_healthcare_fg
        c.contains("clean") -> R.color.cat_cleaning_bg to R.color.cat_cleaning_fg
        else -> R.color.md_secondary_container to R.color.md_on_secondary_container
    }
}
