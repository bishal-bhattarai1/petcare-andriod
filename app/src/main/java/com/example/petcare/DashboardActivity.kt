package com.example.petcare

import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.petcare.data.PetCareDatabase
import com.example.petcare.data.PetEntity
import com.example.petcare.data.SessionManager
import com.example.petcare.data.TaskEntity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.tabs.TabLayout

class DashboardActivity : AppCompatActivity() {
    private lateinit var sessionManager: SessionManager
    private lateinit var database: PetCareDatabase
    private lateinit var recyclerView: RecyclerView
    private lateinit var tabLayout: TabLayout
    private lateinit var layoutEmpty: LinearLayout
    private lateinit var textViewEmptyMessage: TextView
    private var currentTab = 0 

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_dashboard)

        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        toolbar.setOnMenuItemClickListener { menuItem ->
            if (menuItem.itemId == R.id.action_logout) {
                logout()
                true
            } else false
        }

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main_dashboard)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        sessionManager = SessionManager(this)
        database = PetCareDatabase.getDatabase(this)

        findViewById<TextView>(R.id.textViewUserName).text = "Welcome, ${sessionManager.getUserName()}"
        
        layoutEmpty = findViewById(R.id.layoutEmpty)
        textViewEmptyMessage = findViewById(R.id.textViewEmptyMessage)
        
        recyclerView = findViewById(R.id.recyclerView)
        recyclerView.layoutManager = LinearLayoutManager(this)
        
        tabLayout = findViewById(R.id.tabLayout)
        tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                currentTab = tab?.position ?: 0
                loadData()
            }
            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })

        findViewById<FloatingActionButton>(R.id.fabAdd).setOnClickListener {
            if (currentTab == 0) {
                startActivity(Intent(this, AddEditPetActivity::class.java))
            } else {
                startActivity(Intent(this, AddEditTaskActivity::class.java))
            }
        }

        setupSwipeActions()
        loadData()
    }

    private fun logout() {
        sessionManager.logout()
        val intent = Intent(this, MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }

    override fun onResume() {
        super.onResume()
        loadData()
    }

    private fun loadData() {
        val ownerEmail = sessionManager.getUserEmail()
        if (currentTab == 0) {
            val pets = database.petDao().getPetsByOwner(ownerEmail)
            updateEmptyState(pets.isEmpty(), "No pets registered yet")
            recyclerView.adapter = PetAdapter(pets) { pet -> openEditPet(pet) }
        } else {
            val tasks = database.taskDao().getTasksByOwner(ownerEmail)
            updateEmptyState(tasks.isEmpty(), "No care tasks scheduled")
            recyclerView.adapter = TaskAdapter(tasks, 
                onEdit = { task -> openEditTask(task) },
                onToggle = { task, isChecked ->
                    database.taskDao().updateTaskCompletion(task.id, isChecked)
                    loadData()
                }
            )
        }
    }

    private fun updateEmptyState(isEmpty: Boolean, message: String) {
        layoutEmpty.visibility = if (isEmpty) View.VISIBLE else View.GONE
        textViewEmptyMessage.text = message
    }

    private fun openEditPet(pet: PetEntity) {
        val intent = Intent(this, AddEditPetActivity::class.java)
        intent.putExtra("PET_ID", pet.id)
        startActivity(intent)
    }

    private fun openEditTask(task: TaskEntity) {
        val intent = Intent(this, AddEditTaskActivity::class.java)
        intent.putExtra("TASK_ID", task.id)
        startActivity(intent)
    }

    private fun setupSwipeActions() {
        val editBackground = ColorDrawable(Color.parseColor("#4CAF50")) 
        val deleteBackground = ColorDrawable(Color.parseColor("#F44336")) 
        val editIcon = ContextCompat.getDrawable(this, android.R.drawable.ic_menu_edit)
        val deleteIcon = ContextCompat.getDrawable(this, android.R.drawable.ic_menu_delete)

        val swipeHandler = object : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT) {
            override fun onMove(rv: RecyclerView, vh: RecyclerView.ViewHolder, t: RecyclerView.ViewHolder) = false
            
            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val position = viewHolder.adapterPosition
                if (direction == ItemTouchHelper.RIGHT) { // EDIT
                    if (currentTab == 0) {
                        openEditPet((recyclerView.adapter as PetAdapter).items[position])
                    } else {
                        openEditTask((recyclerView.adapter as TaskAdapter).items[position])
                    }
                } else { // DELETE
                    val itemTitle = if (currentTab == 0) {
                        (recyclerView.adapter as PetAdapter).items[position].name
                    } else {
                        "this task"
                    }

                    MaterialAlertDialogBuilder(this@DashboardActivity)
                        .setTitle("Delete Confirmation")
                        .setMessage("Are you sure you want to delete '$itemTitle'?")
                        .setNegativeButton("Cancel") { _, _ ->
                            recyclerView.adapter?.notifyItemChanged(position)
                        }
                        .setPositiveButton("Delete") { _, _ ->
                            if (currentTab == 0) {
                                val pet = (recyclerView.adapter as PetAdapter).items[position]
                                database.petDao().deletePet(pet)
                            } else {
                                val task = (recyclerView.adapter as TaskAdapter).items[position]
                                database.taskDao().deleteTask(task)
                            }
                            loadData()
                        }
                        .setOnCancelListener {
                            recyclerView.adapter?.notifyItemChanged(position)
                        }
                        .show()
                }
            }

            override fun onChildDraw(c: Canvas, rv: RecyclerView, vh: RecyclerView.ViewHolder, dX: Float, dY: Float, actionState: Int, isCurrentlyActive: Boolean) {
                val itemView = vh.itemView
                val iconMargin = (itemView.height - (deleteIcon?.intrinsicHeight ?: 0)) / 2

                if (dX > 0) { // Swiping Right (Edit)
                    editBackground.setBounds(itemView.left, itemView.top, itemView.left + dX.toInt(), itemView.bottom)
                    editBackground.draw(c)
                    editIcon?.let {
                        val iconTop = itemView.top + (itemView.height - it.intrinsicHeight) / 2
                        val iconBottom = iconTop + it.intrinsicHeight
                        it.setBounds(itemView.left + iconMargin, iconTop, itemView.left + iconMargin + it.intrinsicWidth, iconBottom)
                        it.draw(c)
                    }
                } else if (dX < 0) { // Swiping Left (Delete)
                    deleteBackground.setBounds(itemView.right + dX.toInt(), itemView.top, itemView.right, itemView.bottom)
                    deleteBackground.draw(c)
                    deleteIcon?.let {
                        val iconTop = itemView.top + (itemView.height - it.intrinsicHeight) / 2
                        val iconBottom = iconTop + it.intrinsicHeight
                        it.setBounds(itemView.right - iconMargin - it.intrinsicWidth, iconTop, itemView.right - iconMargin, iconBottom)
                        it.draw(c)
                    }
                }
                super.onChildDraw(c, rv, vh, dX, dY, actionState, isCurrentlyActive)
            }
        }
        ItemTouchHelper(swipeHandler).attachToRecyclerView(recyclerView)
    }

    inner class PetAdapter(val items: List<PetEntity>, val onEdit: (PetEntity) -> Unit) : RecyclerView.Adapter<PetAdapter.ViewHolder>() {
        inner class ViewHolder(v: View) : RecyclerView.ViewHolder(v) {
            val name: TextView = v.findViewById(R.id.textViewPetName)
            val breed: TextView = v.findViewById(R.id.textViewPetBreed)
            val ageChip: TextView = v.findViewById(R.id.textViewPetAgeChip)
            val weightChip: TextView = v.findViewById(R.id.textViewPetWeightChip)
            val edit: ImageView = v.findViewById(R.id.buttonEditPet)
        }
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = ViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.item_pet, parent, false))
        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val pet = items[position]
            holder.name.text = pet.name
            holder.breed.text = pet.breed
            
            if (pet.age.isNullOrEmpty()) holder.ageChip.visibility = View.GONE
            else {
                holder.ageChip.visibility = View.VISIBLE
                holder.ageChip.text = "${pet.age} Yrs"
            }
            
            if (pet.weight.isNullOrEmpty()) holder.weightChip.visibility = View.GONE
            else {
                holder.weightChip.visibility = View.VISIBLE
                holder.weightChip.text = "${pet.weight} kg"
            }

            holder.edit.setOnClickListener { onEdit(pet) }
        }
        override fun getItemCount() = items.size
    }

    inner class TaskAdapter(val items: List<TaskEntity>, val onEdit: (TaskEntity) -> Unit, val onToggle: (TaskEntity, Boolean) -> Unit) : RecyclerView.Adapter<TaskAdapter.ViewHolder>() {
        inner class ViewHolder(v: View) : RecyclerView.ViewHolder(v) {
            val desc: TextView = v.findViewById(R.id.textViewTaskDescription)
            val pet: TextView = v.findViewById(R.id.textViewTaskPetName)
            val check: CheckBox = v.findViewById(R.id.checkboxTask)
            val edit: ImageView = v.findViewById(R.id.buttonEditTask)
        }
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = ViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.item_task, parent, false))
        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val task = items[position]
            holder.desc.text = task.description
            holder.pet.text = "Pet: ${task.petName}"
            holder.check.isChecked = task.isCompleted
            
            if (task.isCompleted) {
                holder.desc.paintFlags = holder.desc.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
                holder.desc.alpha = 0.5f
            } else {
                holder.desc.paintFlags = holder.desc.paintFlags and Paint.STRIKE_THRU_TEXT_FLAG.inv()
                holder.desc.alpha = 1.0f
            }

            holder.check.setOnCheckedChangeListener { _, isChecked -> onToggle(task, isChecked) }
            holder.edit.setOnClickListener { onEdit(task) }
        }
        override fun getItemCount() = items.size
    }
}