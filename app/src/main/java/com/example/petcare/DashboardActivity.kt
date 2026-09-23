package com.example.petcare

import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.switchmaterial.SwitchMaterial
import java.text.DecimalFormat
import java.util.Calendar
import kotlin.math.roundToInt

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.RectF

class DashboardActivity : AppCompatActivity() {
    companion object {
        const val EXTRA_START_TAB = "com.example.petcare.EXTRA_START_TAB"
        const val START_TAB_HOME = "home"
        const val START_TAB_TASKS = "tasks"
        const val START_TAB_EXPENSES = "expenses"
        const val START_TAB_PROFILE = "profile"
    }

    private enum class MainTab {
        HOME,
        TASKS,
        EXPENSES,
        PROFILE
    }

    private lateinit var database: AuthDatabaseHelper
    private lateinit var sessionManager: SessionManager
    private lateinit var contentContainer: FrameLayout
    private lateinit var petRecyclerView: RecyclerView
    private lateinit var hostFab: FloatingActionButton

    private val tabPages = linkedMapOf<MainTab, View>()
    private val categories = listOf("Food", "Vet", "Grooming", "Toys")
    private var currentTab = MainTab.HOME
    private var selectedPetId: Long? = null

    private var tasksSearchQuery: String = ""
    private var tasksSelectedFilterId: Int = R.id.chipFilterAll
    private var taskAdapter: TaskAdapter? = null

    private var expensesSearchQuery: String = ""

    private lateinit var sensorManager: android.hardware.SensorManager
    private var shakeDetector: ShakeDetector? = null

    private val pickMedia = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            sessionManager.setProfileImageUri(uri.toString())
            refreshCurrentTab()
            Toast.makeText(this, "Profile picture updated", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_dashboard)
        database = AuthDatabaseHelper(this)
        sessionManager = SessionManager(this)
        contentContainer = findViewById(R.id.tabContentContainer)
        hostFab = findViewById(R.id.fabAdd)
        tabPages[MainTab.HOME] = findViewById(R.id.dashboardScroll)

        sensorManager = getSystemService(android.content.Context.SENSOR_SERVICE) as android.hardware.SensorManager
        shakeDetector = ShakeDetector {
            showResetConfirmation()
        }

        updateStatusBarIcons()

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main_dashboard)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, 0)
            insets
        }

        setupHomePage()
        setupBottomNavigation()
        showTab(tabFromIntent(intent), refresh = true)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        showTab(tabFromIntent(intent), refresh = true)
    }

    override fun onResume() {
        super.onResume()
        val accelerometer = sensorManager.getDefaultSensor(android.hardware.Sensor.TYPE_ACCELEROMETER)
        sensorManager.registerListener(shakeDetector, accelerometer, android.hardware.SensorManager.SENSOR_DELAY_UI)
        refreshCurrentTab()
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
                    refreshCurrentTab()
                    Toast.makeText(this, "Daily routines reset! ☀️", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Not now", null)
            .show()
    }

    private fun setupHomePage() {
        petRecyclerView = findViewById(R.id.recyclerViewPets)
        petRecyclerView.layoutManager = LinearLayoutManager(this)

        findViewById<View>(R.id.buttonProfile).setOnClickListener {
            showTab(MainTab.PROFILE)
        }

        findViewById<View>(R.id.buttonNotifications).setOnClickListener {
            startActivity(Intent(this, NotificationsActivity::class.java))
        }

        findViewById<View>(R.id.cardSummary).setOnClickListener {
            showTab(MainTab.TASKS)
        }

        findViewById<View>(R.id.btnQuickAddPet).setOnClickListener {
            startActivity(Intent(this, AddEditPetActivity::class.java))
        }

        findViewById<View>(R.id.btnQuickAddTask).setOnClickListener {
            startActivity(Intent(this, AddTaskActivity::class.java))
        }

        findViewById<View>(R.id.btnQuickAddExpense).setOnClickListener {
            startActivity(Intent(this, AddExpenseActivity::class.java))
        }

        findViewById<View>(R.id.btnQuickDelegate).setOnClickListener {
            startActivity(Intent(this, DelegateContactActivity::class.java))
        }

        findViewById<View>(R.id.btnQuickLocations).setOnClickListener {
            startActivity(Intent(this, LocationsActivity::class.java))
        }
    }

    private fun setupBottomNavigation() {
        findViewById<View>(R.id.navHome).setOnClickListener { showTab(MainTab.HOME) }
        findViewById<View>(R.id.navTasks).setOnClickListener { showTab(MainTab.TASKS) }
        findViewById<View>(R.id.navExpenses).setOnClickListener { showTab(MainTab.EXPENSES) }
        findViewById<View>(R.id.navProfile).setOnClickListener { showTab(MainTab.PROFILE) }
    }

    private fun showTab(tab: MainTab, refresh: Boolean = true) {
        ensurePage(tab)
        tabPages.forEach { (pageTab, pageView) ->
            pageView.visibility = if (pageTab == tab) View.VISIBLE else View.GONE
        }
        currentTab = tab
        updateBottomNavigationState(tab)
        configureFab(tab)
        if (refresh) refreshCurrentTab()
    }

    private fun ensurePage(tab: MainTab): View {
        tabPages[tab]?.let { return it }

        val layoutId = when (tab) {
            MainTab.HOME -> R.layout.activity_dashboard
            MainTab.TASKS -> R.layout.activity_tasks
            MainTab.EXPENSES -> R.layout.activity_expenses
            MainTab.PROFILE -> R.layout.activity_profile
        }
        val page = LayoutInflater.from(this).inflate(layoutId, contentContainer, false)
        removeEmbeddedNavigation(page)
        contentContainer.addView(
            page,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )
        page.visibility = View.GONE
        tabPages[tab] = page

        when (tab) {
            MainTab.HOME -> Unit
            MainTab.TASKS -> setupTasksPage(page)
            MainTab.EXPENSES -> setupExpensesPage(page)
            MainTab.PROFILE -> setupProfilePage(page)
        }
        return page
    }

    private fun removeEmbeddedNavigation(page: View) {
        listOf(R.id.bottomAppBar, R.id.fabAdd).forEach { id ->
            val chrome = page.findViewById<View>(id)
            (chrome?.parent as? ViewGroup)?.removeView(chrome)
        }
    }

    private fun configureFab(tab: MainTab) {
        hostFab.setOnClickListener {
            when (tab) {
                MainTab.HOME,
                MainTab.PROFILE -> startActivity(Intent(this, AddEditPetActivity::class.java))
                MainTab.TASKS -> startActivity(Intent(this, AddTaskActivity::class.java))
                MainTab.EXPENSES -> {
                    val intent = Intent(this, AddExpenseActivity::class.java)
                    selectedPetId?.let { intent.putExtra(AddExpenseActivity.EXTRA_SELECTED_PET_ID, it) }
                    startActivity(intent)
                }
            }
        }
    }

    private fun updateBottomNavigationState(selectedTab: MainTab) {
        val navItems = listOf(
            MainTab.HOME to findViewById<LinearLayout>(R.id.navHome),
            MainTab.TASKS to findViewById<LinearLayout>(R.id.navTasks),
            MainTab.EXPENSES to findViewById<LinearLayout>(R.id.navExpenses),
            MainTab.PROFILE to findViewById<LinearLayout>(R.id.navProfile)
        )

        navItems.forEach { (tab, item) ->
            val selected = tab == selectedTab
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

    private fun refreshCurrentTab() {
        when (currentTab) {
            MainTab.HOME -> {
                setupDynamicGreeting()
                loadPetsFromDatabase()
            }
            MainTab.TASKS -> renderTasks(ensurePage(MainTab.TASKS))
            MainTab.EXPENSES -> {
                val page = ensurePage(MainTab.EXPENSES)
                setupPetFilters(page)
                loadExpenses(page)
            }
            MainTab.PROFILE -> {
                val page = ensurePage(MainTab.PROFILE)
                bindAccount(page)
                setupPreferences(page)
            }
        }
    }

    private fun setupDynamicGreeting() {
        val name = (sessionManager.getUserName() ?: "Pet Lover").uppercase()
        findViewById<TextView>(R.id.textUserName).text = name

        // Load profile image
        val imageUriString = sessionManager.getProfileImageUri()
        if (imageUriString != null) {
            val imageView = findViewById<ImageView>(R.id.imageDashboardAvatar)
            imageView.setImageURI(Uri.parse(imageUriString))
            imageView.imageTintList = null // Clear tint to show photo
        }

        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val greeting = when (hour) {
            in 5..11 -> "GOOD MORNING"
            in 12..16 -> "GOOD AFTERNOON"
            in 17..20 -> "GOOD EVENING"
            else -> "GOOD NIGHT"
        }
        findViewById<TextView>(R.id.textGreetingLabel).text = greeting

        // Production Level: Dynamic Pet Care Tip of the Day
        val tips = listOf(
            "Regular grooming helps prevent skin issues and keeps your pet's coat healthy.",
            "Walking your dog daily improves their mental health and physical fitness.",
            "Interactive toys can help reduce anxiety and boredom in your pets.",
            "Ensure your pet has a comfortable, quiet place to sleep and rest.",
            "Fresh water should be available 24/7. Clean the bowl daily.",
            "Check for ticks and fleas after every outdoor walk.",
            "Small treats are great, but monitor your pet's calorie intake.",
            "Positive reinforcement is the best way to train Max or Luna!"
        )
        try {
            val randomTip = tips.random()
            findViewById<TextView>(R.id.textCareTip).text = "Care Tip: $randomTip"
        } catch (_: Exception) {}

        // Notification visibility logic
        findViewById<View>(R.id.buttonNotifications).visibility =
            if (sessionManager.areNotificationsEnabled()) View.VISIBLE else View.GONE

        // Emergency Contact logic
        val contact = sessionManager.getDefaultDelegateContact()
        val cardEmergency = findViewById<View>(R.id.cardEmergencyContact)
        if (contact.isNotBlank()) {
            cardEmergency.visibility = View.VISIBLE
            findViewById<TextView>(R.id.textEmergencyContact).text = contact
            cardEmergency.setOnClickListener {
                try {
                    val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$contact"))
                    startActivity(intent)
                } catch (e: Exception) {
                    Toast.makeText(this, "Unable to dial contact", Toast.LENGTH_SHORT).show()
                }
            }
        } else {
            cardEmergency.visibility = View.VISIBLE
            findViewById<TextView>(R.id.textEmergencyContact).text = "Tap to set emergency contact"
            cardEmergency.setOnClickListener {
                startActivity(Intent(this, PersonalInfoActivity::class.java))
            }
        }
    }

    private fun loadPetsFromDatabase() {
        val pets = database.getAllPets()
        petRecyclerView.adapter = PetAdapter(pets)
        val taskStats = database.getTaskStats()
        val petLabel = if (pets.size == 1) "pet" else "pets"

        findViewById<TextView>(R.id.textPetCount).text = "${pets.size} $petLabel"
        findViewById<TextView>(R.id.textDailySummary).text = buildDailySummary(pets.size, taskStats)
        
        val emptyPets = findViewById<TextView>(R.id.textEmptyPets)
        emptyPets.text = "You haven't added any pets yet. Tap the button above to create Max or Luna's profile!"
        emptyPets.visibility = if (pets.isEmpty()) View.VISIBLE else View.GONE

        // Update notification badge if there are remaining tasks
        val remainingTasks = taskStats.totalTasks - taskStats.completedTasks
        findViewById<View>(R.id.viewNotificationBadge).visibility = if (remainingTasks > 0) View.VISIBLE else View.GONE
    }

    private fun buildDailySummary(petCount: Int, taskStats: TaskStats): String {
        if (petCount == 0) return "Add a pet to start tracking care"
        if (taskStats.totalTasks == 0) {
            val petLabel = if (petCount == 1) "pet" else "pets"
            return "$petCount $petLabel saved. Add a care routine from Tasks."
        }

        val remaining = taskStats.totalTasks - taskStats.completedTasks
        val taskLabel = if (remaining == 1) "task" else "tasks"
        return "$remaining $taskLabel remaining across $petCount saved ${if (petCount == 1) "pet" else "pets"}"
    }

    private fun setupTasksPage(page: View) {
        val openAddTask = View.OnClickListener {
            startActivity(Intent(this, AddTaskActivity::class.java))
        }
        page.findViewById<View>(R.id.buttonAddTask).setOnClickListener(openAddTask)

        val searchEdit = page.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.editTasksSearch)
        searchEdit.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
                tasksSearchQuery = s?.toString()?.trim().orEmpty()
                renderTasks(page)
            }
        })

        val filterGroup = page.findViewById<ChipGroup>(R.id.chipGroupTaskFilters)
        filterGroup.setOnCheckedStateChangeListener { _, checkedIds ->
            tasksSelectedFilterId = checkedIds.firstOrNull() ?: R.id.chipFilterAll
            renderTasks(page)
        }

        val rv = page.findViewById<RecyclerView>(R.id.recyclerViewTasks)
        rv.layoutManager = LinearLayoutManager(this)
        
        taskAdapter = TaskAdapter(emptyList(), 
            onComplete = { task -> /* Handled by Swipe Right */ },
            onDelete = { task -> /* Handled by Swipe Left */ },
            onClick = { task -> openChecklist(task) }
        )
        rv.adapter = taskAdapter

        // Production Gesture Handling: Swipe Actions
        val swipeHandler = object : ItemTouchHelper.SimpleCallback(0, 
            ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT) {
            
            override fun onMove(rv: RecyclerView, vh: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder) = false

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val position = viewHolder.absoluteAdapterPosition
                val task = (taskAdapter?.tasks ?: return)[position]

                val vibrator = getSystemService(VIBRATOR_SERVICE) as android.os.Vibrator
                vibrator.vibrate(android.os.VibrationEffect.createOneShot(50, android.os.VibrationEffect.DEFAULT_AMPLITUDE))

                if (direction == ItemTouchHelper.RIGHT) {
                    // Mark as Completed
                    database.updateTaskCompletion(task.id, true)
                    Toast.makeText(this@DashboardActivity, "${task.description} done! 🎉", Toast.LENGTH_SHORT).show()
                    renderTasks(page)
                } else {
                    // Production Logic: Deletion Confirmation with Slide Back
                    MaterialAlertDialogBuilder(this@DashboardActivity)
                        .setTitle("Delete Routine?")
                        .setMessage("Are you sure you want to remove \"${task.description}\" from the checklist?")
                        .setPositiveButton("Delete") { _, _ ->
                            database.deleteTask(task.id)
                            Toast.makeText(this@DashboardActivity, "Task removed", Toast.LENGTH_SHORT).show()
                            renderTasks(page)
                        }
                        .setNegativeButton("Cancel") { dialog, _ ->
                            taskAdapter?.notifyItemChanged(position)
                            dialog.dismiss()
                        }
                        .setOnCancelListener {
                            taskAdapter?.notifyItemChanged(position)
                        }
                        .show()
                }
            }

            override fun onChildDraw(
                c: Canvas, rv: RecyclerView, vh: RecyclerView.ViewHolder,
                dX: Float, dY: Float, actionState: Int, isCurrentlyActive: Boolean
            ) {
                val itemView = vh.itemView
                val itemHeight = itemView.bottom - itemView.top
                val isCanceled = dX == 0f && !isCurrentlyActive

                if (isCanceled) {
                    super.onChildDraw(c, rv, vh, dX, dY, actionState, isCurrentlyActive)
                    return
                }

                val background = ColorDrawable()
                val paint = Paint()
                
                if (dX > 0) { // Swiping to the right (Complete - Green)
                    background.color = Color.parseColor("#4CAF50")
                    background.setBounds(itemView.left, itemView.top, itemView.left + dX.toInt(), itemView.bottom)
                    background.draw(c)
                    
                    val icon = ContextCompat.getDrawable(this@DashboardActivity, R.drawable.ic_status_check)
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
                    background.color = Color.parseColor("#FF4B4B")
                    background.setBounds(itemView.right + dX.toInt(), itemView.top, itemView.right, itemView.bottom)
                    background.draw(c)
                    
                    val icon = ContextCompat.getDrawable(this@DashboardActivity, R.drawable.ic_trash)
                    icon?.let {
                        val iconMargin = (itemHeight - it.intrinsicHeight) / 2
                        val iconTop = itemView.top + iconMargin
                        val iconBottom = iconTop + it.intrinsicHeight
                        val iconRight = itemView.right - iconMargin
                        val iconLeft = iconRight - it.intrinsicWidth
                        it.setBounds(iconLeft, iconTop, iconRight, iconBottom)
                        it.setTint(Color.WHITE)
                        it.draw(c)
                    }
                }

                super.onChildDraw(c, rv, vh, dX, dY, actionState, isCurrentlyActive)
            }
        }
        ItemTouchHelper(swipeHandler).attachToRecyclerView(rv)

        renderTasks(page)
    }

    private fun renderTasks(page: View) {
        var tasks = database.getCareTasks()

        // 1. Live Filter by Category chips selection
        when (tasksSelectedFilterId) {
            R.id.chipFilterFeeding -> {
                tasks = tasks.filter { it.category.contains("Feed", ignoreCase = true) || it.description.contains("feed", ignoreCase = true) }
            }
            R.id.chipFilterHealth -> {
                tasks = tasks.filter { it.category.contains("Health", ignoreCase = true) || it.category.contains("Vet", ignoreCase = true) || it.description.contains("med", ignoreCase = true) }
            }
            R.id.chipFilterGrooming -> {
                tasks = tasks.filter { it.category.contains("Groom", ignoreCase = true) || it.description.contains("groom", ignoreCase = true) }
            }
            R.id.chipFilterWalking -> {
                tasks = tasks.filter { it.category.contains("Walk", ignoreCase = true) || it.description.contains("walk", ignoreCase = true) }
            }
        }

        // 2. Filter by search queries
        if (tasksSearchQuery.isNotBlank()) {
            tasks = tasks.filter {
                it.description.contains(tasksSearchQuery, ignoreCase = true) ||
                it.petName.contains(tasksSearchQuery, ignoreCase = true)
            }
        }

        // 3. Render Progress bar details
        val totalCount = tasks.size
        val completedCount = tasks.count { it.isCompleted }
        val percentage = if (totalCount == 0) 100 else (completedCount * 100) / totalCount

        page.findViewById<TextView>(R.id.textTasksOverviewRatio).text = "$percentage%"
        page.findViewById<com.google.android.material.progressindicator.LinearProgressIndicator>(R.id.progressTasksOverview).progress = percentage

        taskAdapter?.updateTasks(tasks)
        
        page.findViewById<TextView>(R.id.textEmptyTasks).visibility =
            if (tasks.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun openChecklist(task: CareTask) {
        startActivity(
            Intent(this, ChecklistActivity::class.java)
                .putExtra(ChecklistActivity.EXTRA_PET_ID, task.petId)
                .putExtra(ChecklistActivity.EXTRA_PET_NAME, task.petName)
        )
    }

    private fun setupExpensesPage(page: View) {
        page.findViewById<RecyclerView>(R.id.recyclerViewExpenses).layoutManager = LinearLayoutManager(this)
        val openAddExpense = View.OnClickListener {
            val intent = Intent(this, AddExpenseActivity::class.java)
            selectedPetId?.let { intent.putExtra(AddExpenseActivity.EXTRA_SELECTED_PET_ID, it) }
            startActivity(intent)
        }
        page.findViewById<View>(R.id.buttonAddExpense).setOnClickListener(openAddExpense)
        
        val searchEdit = page.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.editExpensesSearch)
        searchEdit.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
                expensesSearchQuery = s?.toString()?.trim().orEmpty()
                loadExpenses(page)
            }
        })

        val rv = page.findViewById<RecyclerView>(R.id.recyclerViewExpenses)
        rv.layoutManager = LinearLayoutManager(this)
        
        // Swipe to Delete for Expenses
        val swipeHandler = object : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT) {
            override fun onMove(r: RecyclerView, v: RecyclerView.ViewHolder, t: RecyclerView.ViewHolder) = false
            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val position = viewHolder.absoluteAdapterPosition
                val adapter = rv.adapter as? ExpenseAdapter ?: return
                val expense = adapter.getExpenseAt(position)
                
                MaterialAlertDialogBuilder(this@DashboardActivity)
                    .setTitle("Delete Transaction?")
                    .setMessage("Remove \"${expense.description}\" permanently?")
                    .setPositiveButton("Delete") { _, _ ->
                        database.deleteExpense(expense.id)
                        loadExpenses(page)
                        Toast.makeText(this@DashboardActivity, "Expense removed", Toast.LENGTH_SHORT).show()
                    }
                    .setNegativeButton("Cancel") { d, _ ->
                        adapter.notifyItemChanged(position)
                        d.dismiss()
                    }
                    .setOnCancelListener { adapter.notifyItemChanged(position) }
                    .show()
            }

            override fun onChildDraw(
                c: Canvas, rv: RecyclerView, vh: RecyclerView.ViewHolder,
                dX: Float, dY: Float, actionState: Int, isCurrentlyActive: Boolean
            ) {
                val itemView = vh.itemView
                val itemHeight = itemView.bottom - itemView.top
                
                if (dX < 0) { // Swiping to the left (Delete - Red)
                    val background = ColorDrawable(Color.parseColor("#FF4B4B"))
                    background.setBounds(itemView.right + dX.toInt(), itemView.top, itemView.right, itemView.bottom)
                    background.draw(c)
                    
                    val icon = ContextCompat.getDrawable(this@DashboardActivity, R.drawable.ic_trash)
                    icon?.let {
                        val iconMargin = (itemHeight - it.intrinsicHeight) / 2
                        val iconTop = itemView.top + iconMargin
                        val iconBottom = iconTop + it.intrinsicHeight
                        val iconRight = itemView.right - iconMargin
                        val iconLeft = iconRight - it.intrinsicWidth
                        it.setBounds(iconLeft, iconTop, iconRight, iconBottom)
                        it.setTint(Color.WHITE)
                        it.draw(c)
                    }
                }
                super.onChildDraw(c, rv, vh, dX, dY, actionState, isCurrentlyActive)
            }
        }
        ItemTouchHelper(swipeHandler).attachToRecyclerView(rv)

        setupPetFilters(page)
        loadExpenses(page)
    }

    private fun setupPetFilters(page: View) {
        val chipGroup = page.findViewById<ChipGroup>(R.id.chipGroupPets)
        chipGroup.removeAllViews()

        addPetChip(page, chipGroup, "All pets", null, selectedPetId == null)
        database.getPetOptions().forEach { pet ->
            addPetChip(page, chipGroup, pet.name, pet.id, selectedPetId == pet.id)
        }
    }

    private fun addPetChip(
        page: View,
        chipGroup: ChipGroup,
        label: String,
        petId: Long?,
        checked: Boolean
    ) {
        val chip = Chip(this).apply {
            id = View.generateViewId()
            text = label
            isCheckable = true
            isChecked = checked
            chipMinHeight = 40.dp().toFloat()
            setTextColor(ContextCompat.getColorStateList(this@DashboardActivity, R.color.chip_selectable_text))
            chipBackgroundColor = ContextCompat.getColorStateList(this@DashboardActivity, R.color.chip_selectable_bg)
            chipStrokeColor = ContextCompat.getColorStateList(this@DashboardActivity, R.color.chip_selectable_stroke)
            chipStrokeWidth = 1f
            checkedIcon = null
            isCheckedIconVisible = false
            setOnClickListener {
                selectedPetId = petId
                setupPetFilters(page)
                loadExpenses(page)
            }
        }
        chipGroup.addView(chip)
    }

    private fun loadExpenses(page: View) {
        val expenses = database.getExpenses(selectedPetId, expensesSearchQuery.ifBlank { null })
        page.findViewById<RecyclerView>(R.id.recyclerViewExpenses).adapter = ExpenseAdapter(expenses)
        page.findViewById<TextView>(R.id.textEmptyExpenses).visibility =
            if (expenses.isEmpty()) View.VISIBLE else View.GONE

        val total = expenses.sumOf { it.amount }
        page.findViewById<TextView>(R.id.textTotalSpend).text = formatCurrency(total)

        // Additional Insights: Monthly Average & Count
        val count = expenses.size
        val avg = if (count > 0) total / count else 0.0
        
        /* 
        try {
            page.findViewById<TextView>(R.id.textTransactionCount).text = "$count entries"
            page.findViewById<TextView>(R.id.textAverageSpend).text = "${formatCurrency(avg)} avg"
        } catch (_: Exception) {} 
        */

        renderBreakdown(page, expenses, total)
    }

    private fun formatCurrency(amount: Double): String {
        return try {
            val format = DecimalFormat("$#,##0.00")
            format.format(amount)
        } catch (e: Exception) {
            "$${String.format("%.2", amount)}"
        }
    }

    private fun renderBreakdown(page: View, expenses: List<ExpenseTransaction>, total: Double) {
        val totalsByCategory = categories.associateWith { category ->
            expenses.filter { it.category == category }.sumOf { it.amount }
        }
        renderBar(page, totalsByCategory, total)
        renderLegend(page, totalsByCategory, total)
    }

    private fun renderBar(page: View, totalsByCategory: Map<String, Double>, total: Double) {
        val bar = page.findViewById<LinearLayout>(R.id.layoutCategoryBar)
        bar.removeAllViews()

        if (total <= 0.0) {
            val emptySegment = View(this).apply {
                setBackgroundColor(ContextCompat.getColor(this@DashboardActivity, R.color.indicator_grey))
            }
            bar.addView(
                emptySegment,
                LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f)
            )
            return
        }

        categories.forEach { category ->
            val value = totalsByCategory[category] ?: 0.0
            if (value <= 0.0) return@forEach

            val segment = View(this).apply {
                setBackgroundColor(ContextCompat.getColor(this@DashboardActivity, category.expenseCategoryStyle().colorRes))
            }
            bar.addView(
                segment,
                LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, value.toFloat())
            )
        }
    }

    private fun renderLegend(page: View, totalsByCategory: Map<String, Double>, total: Double) {
        val legend = page.findViewById<LinearLayout>(R.id.layoutCategoryLegend)
        legend.removeAllViews()

        categories.chunked(2).forEach { rowCategories ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                if (legend.childCount > 0) {
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply { topMargin = 8.dp() }
                }
            }

            rowCategories.forEach { category ->
                row.addView(createLegendItem(category, totalsByCategory[category] ?: 0.0, total))
            }
            legend.addView(row)
        }
    }

    private fun createLegendItem(category: String, amount: Double, total: Double): View {
        val percent = if (total <= 0.0) 0 else ((amount / total) * 100).roundToInt()
        val style = category.expenseCategoryStyle()

        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)

            addView(View(this@DashboardActivity).apply {
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(ContextCompat.getColor(this@DashboardActivity, style.colorRes))
                }
            }, LinearLayout.LayoutParams(8.dp(), 8.dp()))

            val contentLayout = LinearLayout(this@DashboardActivity).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(6.dp(), 0, 0, 0)
            }

            contentLayout.addView(TextView(this@DashboardActivity).apply {
                text = style.label
                setTextColor(ContextCompat.getColor(this@DashboardActivity, R.color.app_text_primary))
                textSize = 12f
                typeface = Typeface.DEFAULT_BOLD
            })

            contentLayout.addView(TextView(this@DashboardActivity).apply {
                text = "${formatCurrency(amount)} ($percent%)"
                setTextColor(ContextCompat.getColor(this@DashboardActivity, R.color.app_text_secondary))
                textSize = 11f
            })

            addView(contentLayout)
        }
    }

    private fun setupProfilePage(page: View) {
        bindAccount(page)
        setupPreferences(page)
        setupProfileActions(page)
    }

    private fun bindAccount(page: View) {
        val name = sessionManager.getUserName().orEmpty().ifBlank { "Pet Lover" }
        val email = sessionManager.getUserEmail().orEmpty().ifBlank { "No email saved" }

        page.findViewById<TextView>(R.id.textProfileNameLarge).text = name
        page.findViewById<TextView>(R.id.textProfileEmailLarge).text = email

        // Load profile image
        val imageUriString = sessionManager.getProfileImageUri()
        if (imageUriString != null) {
            val imageView = page.findViewById<ImageView>(R.id.imageProfileAvatar)
            imageView.setImageURI(Uri.parse(imageUriString))
            imageView.imageTintList = null // Clear tint to show photo
        }

        // Load theme text
        val themeText = when (sessionManager.getThemeMode()) {
            AppCompatDelegate.MODE_NIGHT_NO -> "Light"
            AppCompatDelegate.MODE_NIGHT_YES -> "Dark"
            else -> "System"
        }
        page.findViewById<TextView>(R.id.textCurrentTheme).text = themeText
    }

    private fun setupPreferences(page: View) {
        val notificationsSwitch = page.findViewById<SwitchMaterial>(R.id.switchNotifications)
        notificationsSwitch.setOnCheckedChangeListener(null)
        notificationsSwitch.isChecked = sessionManager.areNotificationsEnabled()
        notificationsSwitch.setOnCheckedChangeListener { _, isChecked ->
            sessionManager.setNotificationsEnabled(isChecked)
            findViewById<View>(R.id.buttonNotifications).visibility =
                if (isChecked) View.VISIBLE else View.GONE
            Toast.makeText(
                this,
                if (isChecked) "Notifications enabled" else "Notifications disabled",
                Toast.LENGTH_SHORT
            ).show()
        }

        page.findViewById<View>(R.id.layoutAppTheme).setOnClickListener {
            val themes = arrayOf("Light", "Dark", "System Default")
            val checkedItem = when (sessionManager.getThemeMode()) {
                AppCompatDelegate.MODE_NIGHT_NO -> 0
                AppCompatDelegate.MODE_NIGHT_YES -> 1
                else -> 2
            }

            MaterialAlertDialogBuilder(this)
                .setTitle("Choose Theme")
                .setSingleChoiceItems(themes, checkedItem) { dialog, which ->
                    val mode = when (which) {
                        0 -> AppCompatDelegate.MODE_NIGHT_NO
                        1 -> AppCompatDelegate.MODE_NIGHT_YES
                        else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
                    }
                    sessionManager.setThemeMode(mode)
                    AppCompatDelegate.setDefaultNightMode(mode)
                    dialog.dismiss()
                    refreshCurrentTab()
                }
                .show()
        }
    }

    private fun setupProfileActions(page: View) {
        page.findViewById<View>(R.id.cardProfileAvatar).setOnClickListener {
            pickMedia.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }

        page.findViewById<View>(R.id.layoutPersonalInfo).setOnClickListener {
            startActivity(Intent(this, PersonalInfoActivity::class.java))
        }

        page.findViewById<View>(R.id.layoutDelegateContact).setOnClickListener {
            startActivity(Intent(this, DelegateContactActivity::class.java))
        }

        page.findViewById<View>(R.id.layoutLocations).setOnClickListener {
            startActivity(Intent(this, LocationsActivity::class.java))
        }

        page.findViewById<View>(R.id.layoutChangePassword).setOnClickListener {
            startActivity(Intent(this, ChangePasswordActivity::class.java))
        }

        page.findViewById<View>(R.id.layoutHelpCenter).setOnClickListener {
            openSupportUrl("https://petcare-app.example.com/help")
        }

        page.findViewById<View>(R.id.layoutPrivacyPolicy).setOnClickListener {
            openSupportUrl("https://petcare-app.example.com/privacy")
        }

        page.findViewById<View>(R.id.layoutTerms).setOnClickListener {
            openSupportUrl("https://petcare-app.example.com/terms")
        }

        page.findViewById<MaterialButton>(R.id.buttonLogout).setOnClickListener {
            MaterialAlertDialogBuilder(this)
                .setTitle("Logout")
                .setMessage("Are you sure you want to logout?")
                .setPositiveButton("Logout") { _, _ ->
                    sessionManager.logout()
                    startActivity(Intent(this, MainActivity::class.java))
                    finishAffinity()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }

    private fun openSupportUrl(url: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "Unable to open link", Toast.LENGTH_SHORT).show()
        }
    }

    private fun tabFromIntent(intent: Intent?): MainTab {
        return when (intent?.getStringExtra(EXTRA_START_TAB)) {
            START_TAB_TASKS -> MainTab.TASKS
            START_TAB_EXPENSES -> MainTab.EXPENSES
            START_TAB_PROFILE -> MainTab.PROFILE
            else -> MainTab.HOME
        }
    }

    private fun Int.dp(): Int = (this * resources.displayMetrics.density).roundToInt()

    private fun updateStatusBarIcons() {
        val isDarkMode = (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES
        WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightStatusBars = !isDarkMode
    }
}
