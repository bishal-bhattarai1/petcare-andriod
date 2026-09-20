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
    val avatarUri: String? = null
)

class PetAdapter(private val pets: List<PetDashboardModel>) :
    RecyclerView.Adapter<PetAdapter.PetViewHolder>() {

    class PetViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val name: TextView = view.findViewById(R.id.textPetName)
        val breed: TextView = view.findViewById(R.id.textPetBreed)
        val alert: TextView = view.findViewById(R.id.textStatusAlert)
        val progress: LinearProgressIndicator = view.findViewById(R.id.petProgressBar)
        val ratio: TextView = view.findViewById(R.id.textTaskRatio)
        val statusBadge: View = view.findViewById(R.id.imageStatusBadge)
        val avatar: android.widget.ImageView = view.findViewById(R.id.imagePetAvatar)
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
        
        if (pet.isCritical) {
            holder.statusBadge.setBackgroundResource(R.drawable.ic_status_alert)
        } else {
            holder.statusBadge.setBackgroundResource(R.drawable.ic_status_check)
        }

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

    private fun Int.dp(): Int = (this * android.content.res.Resources.getSystem().displayMetrics.density).toInt()
}
