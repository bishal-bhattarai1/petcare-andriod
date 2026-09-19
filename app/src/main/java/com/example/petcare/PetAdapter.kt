package com.example.petcare

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.progressindicator.LinearProgressIndicator

data class PetDashboardModel(
    val name: String,
    val breed: String,
    val progress: Int,
    val totalTasks: Int,
    val completedTasks: Int,
    val statusAlert: String? = null,
    val isCritical: Boolean = false
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
    }

    override fun getItemCount() = pets.size
}
