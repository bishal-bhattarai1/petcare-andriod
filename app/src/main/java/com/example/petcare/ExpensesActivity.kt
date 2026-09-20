package com.example.petcare

import android.content.Intent
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.widget.NestedScrollView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.floatingactionbutton.FloatingActionButton
import java.text.NumberFormat
import kotlin.math.roundToInt

class ExpensesActivity : AppCompatActivity() {
    private lateinit var database: AuthDatabaseHelper
    private lateinit var sessionManager: SessionManager
    private lateinit var recyclerView: RecyclerView
    private var selectedPetId: Long? = null

    private val categories = listOf("Food", "Vet", "Grooming", "Toys")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_expenses)
        database = AuthDatabaseHelper(this)
        sessionManager = SessionManager(this)

        WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightStatusBars = true
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main_expenses)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, 0)
            insets
        }

        recyclerView = findViewById(R.id.recyclerViewExpenses)
        recyclerView.layoutManager = LinearLayoutManager(this)

        setupPetFilters()
        setupActions()
    }

    override fun onResume() {
        super.onResume()
        setupPetFilters()
        loadExpenses()
    }

    private fun setupPetFilters() {
        val chipGroup = findViewById<ChipGroup>(R.id.chipGroupPets)
        chipGroup.removeAllViews()

        addPetChip(chipGroup, "All pets", null, selectedPetId == null)
        database.getPetOptions().forEach { pet ->
            addPetChip(chipGroup, pet.name, pet.id, selectedPetId == pet.id)
        }
    }

    private fun addPetChip(
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
            setTextColor(ContextCompat.getColorStateList(this@ExpensesActivity, R.color.chip_selectable_text))
            chipBackgroundColor = ContextCompat.getColorStateList(this@ExpensesActivity, R.color.chip_selectable_bg)
            chipStrokeColor = ContextCompat.getColorStateList(this@ExpensesActivity, R.color.chip_selectable_stroke)
            chipStrokeWidth = 1f
            checkedIcon = null
            isCheckedIconVisible = false
            setOnClickListener {
                selectedPetId = petId
                setupPetFilters()
                loadExpenses()
            }
        }
        chipGroup.addView(chip)
    }

    private fun setupActions() {
        val openAddExpense = View.OnClickListener {
            val intent = Intent(this, AddExpenseActivity::class.java)
            selectedPetId?.let { intent.putExtra(AddExpenseActivity.EXTRA_SELECTED_PET_ID, it) }
            startActivity(intent)
        }

        findViewById<View>(R.id.buttonAddExpense).setOnClickListener(openAddExpense)
        findViewById<FloatingActionButton>(R.id.fabAdd).setOnClickListener(openAddExpense)

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

    private fun loadExpenses() {
        val expenses = database.getExpenses(selectedPetId)
        recyclerView.adapter = ExpenseAdapter(expenses)
        findViewById<TextView>(R.id.textEmptyExpenses).visibility =
            if (expenses.isEmpty()) View.VISIBLE else View.GONE

        val total = expenses.sumOf { it.amount }
        findViewById<TextView>(R.id.textTotalSpend).text =
            NumberFormat.getCurrencyInstance().format(total)

        renderBreakdown(expenses, total)
    }

    private fun renderBreakdown(expenses: List<ExpenseTransaction>, total: Double) {
        val totalsByCategory = categories.associateWith { category ->
            expenses.filter { it.category == category }.sumOf { it.amount }
        }
        renderBar(totalsByCategory, total)
        renderLegend(totalsByCategory, total)
    }

    private fun renderBar(totalsByCategory: Map<String, Double>, total: Double) {
        val bar = findViewById<LinearLayout>(R.id.layoutCategoryBar)
        bar.removeAllViews()

        if (total <= 0.0) {
            val emptySegment = View(this).apply {
                setBackgroundColor(ContextCompat.getColor(this@ExpensesActivity, R.color.indicator_grey))
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
                setBackgroundColor(ContextCompat.getColor(this@ExpensesActivity, category.expenseCategoryStyle().colorRes))
            }
            bar.addView(
                segment,
                LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, value.toFloat())
            )
        }
    }

    private fun renderLegend(totalsByCategory: Map<String, Double>, total: Double) {
        val legend = findViewById<LinearLayout>(R.id.layoutCategoryLegend)
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
            gravity = android.view.Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)

            addView(View(this@ExpensesActivity).apply {
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(ContextCompat.getColor(this@ExpensesActivity, style.colorRes))
                }
            }, LinearLayout.LayoutParams(8.dp(), 8.dp()))

            addView(TextView(this@ExpensesActivity).apply {
                text = "${style.label} $percent%"
                setTextColor(ContextCompat.getColor(this@ExpensesActivity, R.color.app_text_secondary))
                textSize = 12f
                setPadding(6.dp(), 0, 0, 0)
            })
        }
    }

    private fun Int.dp(): Int = (this * resources.displayMetrics.density).roundToInt()

}
