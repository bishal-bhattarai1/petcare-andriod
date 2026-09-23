package com.example.petcare

import android.content.res.ColorStateList
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView

class TaskAdapter(
    var tasks: List<CareTask>,
    private val onComplete: (CareTask) -> Unit,
    private val onDelete: (CareTask) -> Unit,
    private val onClick: (CareTask) -> Unit
) : RecyclerView.Adapter<TaskAdapter.TaskViewHolder>() {

    class TaskViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val card: com.google.android.material.card.MaterialCardView = view as com.google.android.material.card.MaterialCardView
        val checkbox: FrameLayout = view.findViewById(R.id.taskCheckbox)
        val checkImg: ImageView = view.findViewById(R.id.taskCheckImg)
        val description: TextView = view.findViewById(R.id.taskDesc)
        val petBadge: TextView = view.findViewById(R.id.taskPetBadge)
        val schedule: TextView = view.findViewById(R.id.taskSchedule)
        val status: TextView = view.findViewById(R.id.taskStatus)
        val locBadge: TextView = view.findViewById(R.id.taskLocBadge)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TaskViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_task_row, parent, false)
        return TaskViewHolder(view)
    }

    override fun onBindViewHolder(holder: TaskViewHolder, position: Int) {
        val task = tasks[position]
        val ctx = holder.itemView.context

        holder.description.text = task.description.ifBlank { "Care task" }
        holder.petBadge.text = task.petName
        
        val scheduleText = if (task.repeatType == "Weekly" && !task.isCompleted) {
            val total = task.weekDays.split(",").filter { it.isNotBlank() }.size
            val done = task.completedWeekDays.split(",").filter { it.isNotBlank() }.size
            "$done/$total days • ${task.scheduledTime.ifBlank { "Anytime" }}"
        } else {
            "${task.scheduledTime.ifBlank { "Anytime" }} • ${task.repeatType}"
        }
        holder.schedule.text = scheduleText

        // Location Logic
        if (task.linkedLocationId != -1L && !task.locationName.isNullOrBlank()) {
            holder.locBadge.visibility = View.VISIBLE
            holder.locBadge.text = "📍 ${task.locationName}"
            holder.locBadge.setOnClickListener {
                val gmmIntentUri = android.net.Uri.parse("geo:0,0?q=${android.net.Uri.encode(task.locationAddress)}")
                val mapIntent = android.content.Intent(android.content.Intent.ACTION_VIEW, gmmIntentUri)
                mapIntent.setPackage("com.google.android.apps.maps")
                try {
                    ctx.startActivity(mapIntent)
                } catch (e: Exception) {
                    ctx.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, gmmIntentUri))
                }
            }
        } else {
            holder.locBadge.visibility = View.GONE
        }

        if (task.isCompleted) {
            holder.card.strokeColor = ContextCompat.getColor(ctx, R.color.app_divider)
            holder.card.setCardBackgroundColor(ContextCompat.getColor(ctx, R.color.app_input_bg))
            holder.card.alpha = 0.75f
            holder.description.paintFlags = holder.description.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
            holder.description.setTextColor(ContextCompat.getColor(ctx, R.color.app_text_secondary))
            holder.description.typeface = Typeface.DEFAULT
            holder.checkImg.setImageResource(R.drawable.ic_status_check)
            holder.status.text = "Finished"
            holder.status.setTextColor(ContextCompat.getColor(ctx, R.color.status_green))
            holder.card.setOnClickListener(null)
        } else {
            holder.card.strokeColor = android.graphics.Color.TRANSPARENT
            holder.card.setCardBackgroundColor(ContextCompat.getColor(ctx, R.color.card_bg))
            holder.card.alpha = 1.0f
            holder.description.paintFlags = holder.description.paintFlags and Paint.STRIKE_THRU_TEXT_FLAG.inv()
            holder.description.setTextColor(ContextCompat.getColor(ctx, R.color.app_text_primary))
            holder.description.typeface = Typeface.DEFAULT_BOLD
            
            val outlineCircle = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setStroke((1.5 * ctx.resources.displayMetrics.density).toInt(), ContextCompat.getColor(ctx, R.color.app_text_secondary))
                setColor(android.graphics.Color.TRANSPARENT)
            }
            holder.checkImg.setImageDrawable(outlineCircle)
            holder.status.text = "Checklist"
            holder.status.setTextColor(ContextCompat.getColor(ctx, R.color.app_text_secondary))
            holder.card.setOnClickListener { onClick(task) }
        }

        holder.checkbox.setOnClickListener { if (!task.isCompleted) onClick(task) }
    }

    override fun getItemCount() = tasks.size

    fun updateTasks(newTasks: List<CareTask>) {
        this.tasks = newTasks
        notifyDataSetChanged()
    }
}
