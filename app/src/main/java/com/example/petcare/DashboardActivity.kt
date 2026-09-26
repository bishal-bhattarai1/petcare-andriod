package com.example.petcare

import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
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
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.switchmaterial.SwitchMaterial
import kotlin.math.roundToInt


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
    private lateinit var hostFab: FloatingActionButton

    private val tabPages = linkedMapOf<MainTab, View>()
    private var currentTab = MainTab.HOME

    private var homePage: HomePageController? = null
    private var tasksPage: TasksPageController? = null

    private var expensesPage: ExpensesPageController? = null

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

    override fun onDestroy() {
        homePage?.release()
        expensesPage?.release()
        super.onDestroy()
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
        homePage = HomePageController(
            activity = this,
            page = findViewById(R.id.dashboardScroll),
            database = database,
            sessionManager = sessionManager,
            onOpenTasks = { showTab(MainTab.TASKS) },
            onOpenExpenses = { showTab(MainTab.EXPENSES) },
            onOpenProfile = { showTab(MainTab.PROFILE) }
        ).also { it.setup() }
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
                MainTab.EXPENSES -> expensesPage?.openAddExpense()
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
            label?.setTypeface(figtree(), if (selected) Typeface.BOLD else Typeface.NORMAL)
        }
    }

    private fun refreshCurrentTab() {
        when (currentTab) {
            MainTab.HOME -> homePage?.refresh()
            MainTab.TASKS -> {
                ensurePage(MainTab.TASKS)
                tasksPage?.refresh()
            }
            MainTab.EXPENSES -> {
                ensurePage(MainTab.EXPENSES)
                expensesPage?.refresh()
            }
            MainTab.PROFILE -> {
                val page = ensurePage(MainTab.PROFILE)
                bindAccount(page)
                setupPreferences(page)
            }
        }
    }

    private fun setupTasksPage(page: View) {
        tasksPage = TasksPageController(
            activity = this,
            page = page,
            database = database,
            sessionManager = sessionManager
        ).also { it.setup() }
    }

    private fun setupExpensesPage(page: View) {
        expensesPage = ExpensesPageController(
            activity = this,
            page = page,
            database = database,
            snackbarAnchor = { hostFab }
        ).also { it.setup() }
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
        val themeText = if (sessionManager.getThemeMode() == AppCompatDelegate.MODE_NIGHT_YES) "Dark" else "Light"
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
            val themes = arrayOf("Light", "Dark")
            val checkedItem = if (sessionManager.getThemeMode() == AppCompatDelegate.MODE_NIGHT_YES) 1 else 0

            MaterialAlertDialogBuilder(this)
                .setTitle("App theme")
                .setSingleChoiceItems(themes, checkedItem) { dialog, which ->
                    val mode = if (which == 1) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO
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
            InfoActivity.open(this, InfoActivity.Page.HELP)
        }

        page.findViewById<View>(R.id.layoutPrivacyPolicy).setOnClickListener {
            InfoActivity.open(this, InfoActivity.Page.PRIVACY)
        }

        page.findViewById<View>(R.id.layoutTerms).setOnClickListener {
            InfoActivity.open(this, InfoActivity.Page.TERMS)
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
