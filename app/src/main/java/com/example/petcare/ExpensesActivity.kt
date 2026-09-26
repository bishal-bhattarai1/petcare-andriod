package com.example.petcare

import android.content.res.ColorStateList
import android.graphics.Typeface
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.google.android.material.floatingactionbutton.FloatingActionButton
import kotlin.math.roundToInt

class ExpensesActivity : AppCompatActivity() {
    private lateinit var expensesPage: ExpensesPageController

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_expenses)

        updateStatusBarIcons()
        updateBottomNavigationUI()

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main_expenses)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, 0)
            insets
        }

        val fab = findViewById<FloatingActionButton>(R.id.fabAdd)
        expensesPage = ExpensesPageController(
            activity = this,
            page = findViewById(R.id.main_expenses),
            database = AuthDatabaseHelper(this),
            snackbarAnchor = { fab }
        ).also { it.setup() }

        fab.setOnClickListener { expensesPage.openAddExpense() }
        setupNavigation()
    }

    override fun onResume() {
        super.onResume()
        expensesPage.refresh()
    }

    override fun onDestroy() {
        expensesPage.release()
        super.onDestroy()
    }

    private fun setupNavigation() {
        findViewById<View>(R.id.navHome).setOnClickListener {
            openMainTab(DashboardActivity::class.java)
        }
        findViewById<View>(R.id.navTasks).setOnClickListener {
            openMainTab(TasksActivity::class.java)
        }
        findViewById<View>(R.id.navExpenses).setOnClickListener {
            // Already on Expenses.
        }
        findViewById<View>(R.id.navProfile).setOnClickListener {
            openMainTab(ProfileActivity::class.java)
        }
    }

    private fun Int.dp(): Int = (this * resources.displayMetrics.density).roundToInt()

    private fun updateStatusBarIcons() {
        val isDarkMode = (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES
        WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightStatusBars = !isDarkMode
    }

    private fun updateBottomNavigationUI() {
        val navItems = listOf(
            R.id.navHome to false,
            R.id.navTasks to false,
            R.id.navExpenses to true,
            R.id.navProfile to false
        )

        navItems.forEach { (viewId, selected) ->
            val item = findViewById<LinearLayout>(viewId)
            item.setBackgroundResource(if (selected) R.drawable.bg_bottom_nav_selected else 0)
            (item.layoutParams as? LinearLayout.LayoutParams)?.let { params ->
                val margin = if (selected) 4.dp() else 0
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
