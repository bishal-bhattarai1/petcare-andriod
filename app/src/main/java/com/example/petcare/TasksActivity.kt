package com.example.petcare

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import android.content.res.ColorStateList
import android.graphics.Typeface
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.floatingactionbutton.FloatingActionButton
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import kotlin.math.roundToInt

class TasksActivity : AppCompatActivity() {
    private lateinit var database: AuthDatabaseHelper
    private lateinit var sensorManager: android.hardware.SensorManager
    private var shakeDetector: ShakeDetector? = null
    private var taskAdapter: TaskAdapter? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_tasks)
        database = AuthDatabaseHelper(this)

        sensorManager = getSystemService(android.content.Context.SENSOR_SERVICE) as android.hardware.SensorManager
        shakeDetector = ShakeDetector {
            showResetConfirmation()
        }

        updateStatusBarIcons()
        updateBottomNavigationUI()

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main_tasks)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, 0)
            insets
        }

        setupActions()
        setupBottomNavigation()
    }

    override fun onResume() {
        super.onResume()
        val accelerometer = sensorManager.getDefaultSensor(android.hardware.Sensor.TYPE_ACCELEROMETER)
        sensorManager.registerListener(shakeDetector, accelerometer, android.hardware.SensorManager.SENSOR_DELAY_UI)
        renderTasks()
    }

    override fun onPause() {
        super.onPause()
        sensorManager.unregisterListener(shakeDetector)
    }

    private fun showResetConfirmation() {
        MaterialAlertDialogBuilder(this)
            .setTitle("Reset Checklist")
            .setMessage("Shake detected! Would you like to reset all of today's completed routines for a fresh start?")
            .setPositiveButton("Reset Now") { _, _ ->
                if (database.resetDailyTasks()) {
                    renderTasks()
                    Toast.makeText(this, "Daily routines reset! ☀️", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Not now", null)
            .show()
    }

    private fun setupActions() {
        val openAddTask = View.OnClickListener {
            startActivity(Intent(this, AddTaskActivity::class.java))
        }
        findViewById<View>(R.id.buttonAddTask).setOnClickListener(openAddTask)
        findViewById<FloatingActionButton>(R.id.fabAdd).setOnClickListener(openAddTask)
    }

    private fun setupBottomNavigation() {
        findViewById<View>(R.id.navHome).setOnClickListener {
            openMainTab(DashboardActivity::class.java)
        }
        findViewById<View>(R.id.navTasks).setOnClickListener {
            // Already on Tasks.
        }
        findViewById<View>(R.id.navExpenses).setOnClickListener {
            openMainTab(ExpensesActivity::class.java)
        }
        findViewById<View>(R.id.navProfile).setOnClickListener {
            openMainTab(ProfileActivity::class.java)
        }
    }

    private fun renderTasks() {
        val tasks = database.getCareTasks()
        val rv = findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.recyclerViewTasks)
        rv.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(this)
        
        if (taskAdapter == null) {
            taskAdapter = TaskAdapter(tasks, 
                onComplete = { task -> /* Swipe right */ },
                onDelete = { task -> /* Swipe left */ },
                onClick = { task -> openChecklist(task) }
            )
            rv.adapter = taskAdapter

            // Production Swipe Actions
            val swipeHandler = object : ItemTouchHelper.SimpleCallback(0, 
                ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT) {
                
                override fun onMove(r: RecyclerView, v: RecyclerView.ViewHolder, t: RecyclerView.ViewHolder) = false

                override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                    val position = viewHolder.absoluteAdapterPosition
                    val task = (taskAdapter?.tasks ?: return)[position]

                    if (direction == ItemTouchHelper.RIGHT) {
                        database.updateTaskCompletion(task.id, true)
                        Toast.makeText(this@TasksActivity, "${task.description} completed! 🎉", Toast.LENGTH_SHORT).show()
                        renderTasks()
                    } else {
                        MaterialAlertDialogBuilder(this@TasksActivity)
                            .setTitle("Delete Routine?")
                            .setMessage("Remove \"${task.description}\" permanently?")
                            .setPositiveButton("Delete") { _, _ ->
                                database.deleteTask(task.id)
                                renderTasks()
                            }
                            .setNegativeButton("Cancel") { d, _ ->
                                taskAdapter?.notifyItemChanged(position)
                                d.dismiss()
                            }
                            .setOnCancelListener { taskAdapter?.notifyItemChanged(position) }
                            .show()
                    }
                }

                override fun onChildDraw(
                    c: Canvas, rv: RecyclerView, vh: RecyclerView.ViewHolder,
                    dX: Float, dY: Float, actionState: Int, isCurrentlyActive: Boolean
                ) {
                    val itemView = vh.itemView
                    val itemHeight = itemView.bottom - itemView.top

                    if (dX > 0) { // Swiping to the right (Complete - Green)
                        val background = ColorDrawable(Color.parseColor("#4CAF50"))
                        background.setBounds(itemView.left, itemView.top, itemView.left + dX.toInt(), itemView.bottom)
                        background.draw(c)
                        
                        val icon = ContextCompat.getDrawable(this@TasksActivity, R.drawable.ic_status_check)
                        icon?.let {
                            val iconMargin = (itemHeight - it.intrinsicHeight) / 2
                            val iconTop = itemView.top + iconMargin
                            val iconBottom = iconTop + it.intrinsicHeight
                            val iconLeft = itemView.left + iconMargin
                            val iconRight = iconLeft + it.intrinsicWidth
                            it.setBounds(iconLeft, iconTop, iconRight, iconBottom)
                            it.setTint(Color.WHITE)
                            it.draw(c)
                        }
                    } else if (dX < 0) { // Swiping to the left (Delete - Red)
                        val background = ColorDrawable(Color.parseColor("#FF4B4B"))
                        background.setBounds(itemView.right + dX.toInt(), itemView.top, itemView.right, itemView.bottom)
                        background.draw(c)
                    }
                    super.onChildDraw(c, rv, vh, dX, dY, actionState, isCurrentlyActive)
                }
            }
            ItemTouchHelper(swipeHandler).attachToRecyclerView(rv)
        } else {
            taskAdapter?.updateTasks(tasks)
        }

        findViewById<TextView>(R.id.textEmptyTasks).visibility =
            if (tasks.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun openChecklist(task: CareTask) {
        startActivity(
            Intent(this, ChecklistActivity::class.java)
                .putExtra(ChecklistActivity.EXTRA_PET_ID, task.petId)
                .putExtra(ChecklistActivity.EXTRA_PET_NAME, task.petName)
        )
    }

    private fun updateStatusBarIcons() {
        val isDarkMode = (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES
        WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightStatusBars = !isDarkMode
    }

    private fun updateBottomNavigationUI() {
        val navItems = listOf(
            R.id.navHome to false,
            R.id.navTasks to true,
            R.id.navExpenses to false,
            R.id.navProfile to false
        )

        navItems.forEach { (viewId, isSelected) ->
            val item = findViewById<LinearLayout>(viewId)
            val selected = isSelected
            item.setBackgroundResource(if (selected) R.drawable.bg_bottom_nav_selected else 0)
            (item.layoutParams as? LinearLayout.LayoutParams)?.let { params ->
                val margin = if (selected) (4 * resources.displayMetrics.density).toInt() else 0
                params.marginStart = margin
                params.marginEnd = margin
                item.layoutParams = params
            }

            val color = ContextCompat.getColor(
                this,
                if (selected) R.color.black else R.color.app_text_secondary
            )
            val image = item.getChildAt(0) as? ImageView
            val label = item.getChildAt(1) as? TextView
            image?.imageTintList = ColorStateList.valueOf(color)
            label?.setTextColor(color)
            label?.setTypeface(null, if (selected) Typeface.BOLD else Typeface.NORMAL)
        }
    }
}
