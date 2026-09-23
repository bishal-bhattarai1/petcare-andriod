package com.example.petcare

import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.progressindicator.LinearProgressIndicator

data class PetDashboardModel(
    val id: Long,
    val name: String,
    val breed: String,
    val progress: Int,
    val totalTasks: Int,
    val completedTasks: Int,
    val statusAlert: String? = null,
    val isCritical: Boolean = false,
    val avatarUri: String? = null,
    val createdAt: Long = 0,
    val isFed: Boolean = false,
    val isWalked: Boolean = false,
    val isMedsTaken: Boolean = false,
    val isGroomed: Boolean = false
)

class PetAdapter(private val pets: List<PetDashboardModel>) :
    RecyclerView.Adapter<PetAdapter.PetViewHolder>() {

    class PetViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val name: TextView = view.findViewById(R.id.textPetName)
        val breed: TextView = view.findViewById(R.id.textPetBreed)
        val alert: TextView = view.findViewById(R.id.textStatusAlert)
        val progress: LinearProgressIndicator = view.findViewById(R.id.petProgressBar)
        val ratio: TextView = view.findViewById(R.id.textTaskRatio)
        val avatar: android.widget.ImageView = view.findViewById(R.id.imagePetAvatar)
        val festive: TextView = view.findViewById(R.id.textFestiveBadge)
        val chipFed: TextView = view.findViewById(R.id.chipFed)
        val chipWalked: TextView = view.findViewById(R.id.chipWalked)
        val chipMeds: TextView = view.findViewById(R.id.chipMeds)
        val chipGroom: TextView = view.findViewById(R.id.chipGroom)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PetViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_pet_dashboard, parent, false)
        return PetViewHolder(view)
    }

    override fun onBindViewHolder(holder: PetViewHolder, position: Int) {
        val pet = pets[position]
        holder.name.text = pet.name
        holder.breed.text = pet.breed
        
        if (pet.statusAlert != null) {
            holder.alert.text = pet.statusAlert
            holder.alert.visibility = View.VISIBLE
        } else {
            holder.alert.visibility = View.GONE
        }
        
        holder.progress.progress = pet.progress
        holder.ratio.text = "${pet.completedTasks}/${pet.totalTasks}"

        // Festive Logic: Check if today is the yearly anniversary of pet creation
        val calendar = java.util.Calendar.getInstance()
        val todayDay = calendar.get(java.util.Calendar.DAY_OF_MONTH)
        val todayMonth = calendar.get(java.util.Calendar.MONTH)
        val todayYear = calendar.get(java.util.Calendar.YEAR)
        
        calendar.timeInMillis = pet.createdAt
        val createDay = calendar.get(java.util.Calendar.DAY_OF_MONTH)
        val createMonth = calendar.get(java.util.Calendar.MONTH)
        val createYear = calendar.get(java.util.Calendar.YEAR)
        
        val isAnniversary = todayDay == createDay && todayMonth == createMonth && todayYear > createYear
        
        if (isAnniversary && pet.createdAt > 0) {
            holder.festive.visibility = View.VISIBLE
        } else {
            holder.festive.visibility = View.GONE
        }

        // Bind Care Category Chips
        bindCareChip(holder.chipFed, pet.isFed, R.color.status_green)
        bindCareChip(holder.chipWalked, pet.isWalked, R.color.app_accent_blue)
        bindCareChip(holder.chipMeds, pet.isMedsTaken, R.color.app_accent_red)
        bindCareChip(holder.chipGroom, pet.isGroomed, R.color.indicator_dark)

        if (pet.avatarUri != null) {
            try {
                holder.avatar.setImageURI(android.net.Uri.parse(pet.avatarUri))
                holder.avatar.imageTintList = null
                holder.avatar.setPadding(0, 0, 0, 0)
                holder.avatar.scaleType = android.widget.ImageView.ScaleType.CENTER_CROP
            } catch (_: Exception) {}
        } else {
            holder.avatar.setImageResource(R.drawable.ic_paw)
            holder.avatar.imageTintList = android.content.res.ColorStateList.valueOf(androidx.core.content.ContextCompat.getColor(holder.itemView.context, R.color.indicator_dark))
            holder.avatar.setPadding(12.dp(), 12.dp(), 12.dp(), 12.dp())
        }

        holder.itemView.setOnClickListener {
            val context = holder.itemView.context
            val intent = android.content.Intent(context, PetDetailsActivity::class.java)
            intent.putExtra("EXTRA_PET_ID", pet.id)
            context.startActivity(intent)
        }
    }

    override fun getItemCount() = pets.size

    private fun bindCareChip(view: TextView, isDone: Boolean, activeColorRes: Int) {
        val context = view.context
        if (isDone) {
            val activeColor = androidx.core.content.ContextCompat.getColor(context, activeColorRes)
            view.setTextColor(activeColor)
            androidx.core.widget.TextViewCompat.setCompoundDrawableTintList(
                view, 
                android.content.res.ColorStateList.valueOf(activeColor)
            )
            // Use a subtle version of the active color for background if possible, or just keep it clean
            view.setBackgroundResource(R.drawable.bg_pill_button_grey) // We'll customize this
            view.backgroundTintList = android.content.res.ColorStateList.valueOf(activeColor).withAlpha(30)
            view.alpha = 1.0f
        } else {
            view.setTextColor(androidx.core.content.ContextCompat.getColor(context, R.color.app_text_secondary))
            androidx.core.widget.TextViewCompat.setCompoundDrawableTintList(
                view, 
                android.content.res.ColorStateList.valueOf(androidx.core.content.ContextCompat.getColor(context, R.color.indicator_grey))
            )
            view.setBackgroundResource(R.drawable.bg_pill_button_grey)
            view.backgroundTintList = null
            view.alpha = 0.5f
        }
    }

    private fun Int.dp(): Int = (this * android.content.res.Resources.getSystem().displayMetrics.density).toInt()
}
