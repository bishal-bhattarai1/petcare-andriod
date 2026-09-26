package com.example.petcare

import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
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
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.switchmaterial.SwitchMaterial

class ProfileActivity : AppCompatActivity() {
    private lateinit var sessionManager: SessionManager
    private lateinit var database: AuthDatabaseHelper

    private val pickMedia = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            sessionManager.setProfileImageUri(uri.toString())
            val imageView = findViewById<ImageView>(R.id.imageProfileAvatar)
            imageView.setImageURI(uri)
            imageView.imageTintList = null // Clear the tint to show the actual photo
            Toast.makeText(this, "Profile picture updated", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_profile)
        sessionManager = SessionManager(this)
        database = AuthDatabaseHelper(this)

        updateStatusBarIcons()
        updateBottomNavigationUI()

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main_profile)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, 0)
            insets
        }

        bindAccount()
        setupPreferences()
        setupActions()
        setupBottomNavigation()
    }

    private fun bindAccount() {
        val name = sessionManager.getUserName().orEmpty().ifBlank { "Pet Lover" }
        val email = sessionManager.getUserEmail().orEmpty().ifBlank { "No email saved" }

        findViewById<TextView>(R.id.textProfileNameLarge).text = name
        findViewById<TextView>(R.id.textProfileEmailLarge).text = email

        // Load profile image
        val imageUriString = sessionManager.getProfileImageUri()
        if (imageUriString != null) {
            val imageView = findViewById<ImageView>(R.id.imageProfileAvatar)
            imageView.setImageURI(Uri.parse(imageUriString))
            imageView.imageTintList = null // Clear the tint to show the actual photo
        }

        // Load theme text
        val themeText = if (sessionManager.getThemeMode() == AppCompatDelegate.MODE_NIGHT_YES) "Dark" else "Light"
        findViewById<TextView>(R.id.textCurrentTheme).text = themeText
    }

    private fun setupPreferences() {
        val notificationsSwitch = findViewById<SwitchMaterial>(R.id.switchNotifications)
        notificationsSwitch.isChecked = sessionManager.areNotificationsEnabled()
        notificationsSwitch.setOnCheckedChangeListener { _, isChecked ->
            sessionManager.setNotificationsEnabled(isChecked)
            Toast.makeText(
                this,
                if (isChecked) "Notifications enabled" else "Notifications disabled",
                Toast.LENGTH_SHORT
            ).show()
        }

        findViewById<View>(R.id.layoutAppTheme).setOnClickListener {
            val themes = arrayOf("Light", "Dark")
            val checkedItem = if (sessionManager.getThemeMode() == AppCompatDelegate.MODE_NIGHT_YES) 1 else 0

            MaterialAlertDialogBuilder(this)
                .setTitle("App theme")
                .setSingleChoiceItems(themes, checkedItem) { dialog, which ->
                    val mode = if (which == 1) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO
                    sessionManager.setThemeMode(mode)
                    AppCompatDelegate.setDefaultNightMode(mode)
                    dialog.dismiss()
                    bindAccount()
                }
                .show()
        }
    }

    private fun setupActions() {
        findViewById<View>(R.id.cardProfileAvatar).setOnClickListener {
            pickMedia.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }

        findViewById<View>(R.id.layoutPersonalInfo).setOnClickListener {
            startActivity(Intent(this, PersonalInfoActivity::class.java))
        }

        findViewById<View>(R.id.layoutDelegateContact).setOnClickListener {
            startActivity(Intent(this, DelegateContactActivity::class.java))
        }

        findViewById<View>(R.id.layoutLocations).setOnClickListener {
            startActivity(Intent(this, LocationsActivity::class.java))
        }

        findViewById<View>(R.id.layoutChangePassword).setOnClickListener {
            startActivity(Intent(this, ChangePasswordActivity::class.java))
        }

        findViewById<View>(R.id.layoutHelpCenter).setOnClickListener {
            InfoActivity.open(this, InfoActivity.Page.HELP)
        }

        findViewById<View>(R.id.layoutPrivacyPolicy).setOnClickListener {
            InfoActivity.open(this, InfoActivity.Page.PRIVACY)
        }

        findViewById<View>(R.id.layoutTerms).setOnClickListener {
            InfoActivity.open(this, InfoActivity.Page.TERMS)
        }

        findViewById<MaterialButton>(R.id.buttonLogout).setOnClickListener {
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

    private fun setupBottomNavigation() {
        findViewById<View>(R.id.navHome).setOnClickListener {
            openMainTab(DashboardActivity::class.java)
        }

        findViewById<View>(R.id.navTasks).setOnClickListener {
            openMainTab(TasksActivity::class.java)
        }

        findViewById<View>(R.id.navExpenses).setOnClickListener {
            openMainTab(ExpensesActivity::class.java)
        }

        findViewById<View>(R.id.navProfile).setOnClickListener {
            // Already on Profile.
        }

        findViewById<FloatingActionButton>(R.id.fabAdd).setOnClickListener {
            startActivity(Intent(this, AddEditPetActivity::class.java))
        }
    }

    private fun updateStatusBarIcons() {
        val isDarkMode = (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES
        WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightStatusBars = !isDarkMode
    }

    private fun updateBottomNavigationUI() {
        val navItems = listOf(
            R.id.navHome to false,
            R.id.navTasks to false,
            R.id.navExpenses to false,
            R.id.navProfile to true
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
            label?.setTypeface(figtree(), if (selected) Typeface.BOLD else Typeface.NORMAL)
        }
    }
}
