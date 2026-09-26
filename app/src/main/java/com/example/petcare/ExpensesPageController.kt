package com.example.petcare

import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Canvas
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputEditText
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.math.roundToInt

/**
 * Drives the Spending page (layout `activity_expenses`). Shared by [ExpensesActivity] and the
 * Expenses tab embedded in [DashboardActivity] so both behave identically.
 */
class ExpensesPageController(
    private val activity: AppCompatActivity,
    private val page: View,
    private val database: AuthDatabaseHelper,
    /** View that snackbars should sit above (the FAB), if any. */
    private val snackbarAnchor: () -> View? = { null }
) {
    private enum class Period(val label: String, val totalLabel: String) {
        ALL("All time", "Total spending"),
        THIS_MONTH("This month", "Spent this month"),
        LAST_30_DAYS("Last 30 days", "Spent in the last 30 days"),
        THIS_YEAR("This year", "Spent this year")
    }

    /** Pet filter; also used by hosts to preselect the pet when adding an expense. */
    var selectedPetId: Long? = null
        private set

    private var period = Period.ALL
    private var searchQuery = ""
    private var pets: List<PetOption> = emptyList()
    private var allExpenses: List<ExpenseTransaction> = emptyList()
    private var parsedDates: Map<Long, Date?> = emptyMap()
    private var visibleExpenses: List<ExpenseTransaction> = emptyList()

    private val executor: ExecutorService = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val searchRunnable = Runnable { applyFilters() }
    private val adapter = ExpenseAdapter()
    private val density = activity.resources.displayMetrics.density

    private val recycler: RecyclerView = page.findViewById(R.id.recyclerViewExpenses)
    private val petChips: ChipGroup = page.findViewById(R.id.chipGroupPets)
    private val periodChips: ChipGroup = page.findViewById(R.id.chipGroupPeriod)
    private val emptyText: TextView = page.findViewById(R.id.textEmptyExpenses)
    private val swipeHint: TextView = page.findViewById(R.id.textSwipeHint)
    private val subtitle: TextView = page.findViewById(R.id.textExpensesSubtitle)
    private val totalLabel: TextView = page.findViewById(R.id.textTotalLabel)
    private val totalSpend: TextView = page.findViewById(R.id.textTotalSpend)
    private val statCount: TextView = page.findViewById(R.id.textStatCount)
    private val statAverage: TextView = page.findViewById(R.id.textStatAverage)
    private val statTopCategory: TextView = page.findViewById(R.id.textStatTopCategory)
    private val categoryBar: LinearLayout = page.findViewById(R.id.layoutCategoryBar)
    private val categoryLegend: LinearLayout = page.findViewById(R.id.layoutCategoryLegend)

    fun setup() {
        recycler.layoutManager = LinearLayoutManager(activity)
        recycler.adapter = adapter
        recycler.itemAnimator?.changeDuration = 0
        attachSwipeToDelete()

        page.findViewById<View>(R.id.buttonAddExpense).setOnClickListener { openAddExpense() }
        page.findViewById<View>(R.id.buttonExportExpenses).setOnClickListener { showExportOptions() }

        page.findViewById<TextInputEditText>(R.id.editExpensesSearch).addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                searchQuery = s?.toString()?.trim().orEmpty()
                mainHandler.removeCallbacks(searchRunnable)
                mainHandler.postDelayed(searchRunnable, SEARCH_DEBOUNCE_MS)
            }
        })

        Period.entries.forEach { p ->
            periodChips.addView(createChip(p.label, p).also { if (p == period) it.isChecked = true })
        }
        periodChips.setOnCheckedStateChangeListener { group, ids ->
            period = ids.firstOrNull()?.let { group.findViewById<Chip>(it)?.tag as? Period } ?: return@setOnCheckedStateChangeListener
            applyFilters()
        }
        petChips.setOnCheckedStateChangeListener { group, ids ->
            val chip = ids.firstOrNull()?.let { group.findViewById<Chip>(it) } ?: return@setOnCheckedStateChangeListener
            selectedPetId = chip.tag as? Long
            applyFilters()
        }
    }

    /** Reloads pets and expenses in the background (e.g. after returning from Add Expense). */
    fun refresh() {
        runInBackground({
            val loadedPets = database.getPetOptions()
            val loadedExpenses = database.getExpenses()
            Triple(loadedPets, loadedExpenses, loadedExpenses.associate { it.id to parseExpenseDate(it.date) })
        }) { (loadedPets, loadedExpenses, dates) ->
            if (loadedPets != pets) {
                pets = loadedPets
                if (selectedPetId != null && pets.none { it.id == selectedPetId }) selectedPetId = null
                rebuildPetChips()
            }
            allExpenses = loadedExpenses
            parsedDates = dates
            applyFilters()
        }
    }

    fun release() {
        mainHandler.removeCallbacks(searchRunnable)
        executor.shutdownNow()
    }

    fun openAddExpense() {
        val intent = Intent(activity, AddExpenseActivity::class.java)
        selectedPetId?.let { intent.putExtra(AddExpenseActivity.EXTRA_SELECTED_PET_ID, it) }
        activity.startActivity(intent)
    }

    // region Filtering & rendering

    private fun applyFilters() {
        val start = periodStart()
        visibleExpenses = allExpenses
            .asSequence()
            .filter { selectedPetId == null || it.petId == selectedPetId }
            .filter { start == null || (parsedDates[it.id]?.let { d -> !d.before(start) } ?: false) }
            .filter { searchQuery.isBlank() || it.matches(searchQuery) }
            .sortedWith(compareByDescending<ExpenseTransaction> { parsedDates[it.id]?.time ?: Long.MIN_VALUE }.thenByDescending { it.id })
            .toList()

        adapter.submitList(buildListItems(visibleExpenses))
        renderSummary(visibleExpenses)
        renderBreakdown(visibleExpenses)

        val isEmpty = visibleExpenses.isEmpty()
        swipeHint.visibility = if (isEmpty) View.GONE else View.VISIBLE
        emptyText.visibility = if (isEmpty) View.VISIBLE else View.GONE
        emptyText.text = when {
            allExpenses.isEmpty() -> "No expenses yet. Tap + to add one."
            searchQuery.isNotBlank() -> "No expenses match \"$searchQuery\"."
            else -> "No expenses for this period."
        }
    }

    private fun ExpenseTransaction.matches(query: String): Boolean =
        description.contains(query, ignoreCase = true) ||
            petName.contains(query, ignoreCase = true) ||
            category.contains(query, ignoreCase = true)

    private fun periodStart(): Date? {
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        return when (period) {
            Period.ALL -> null
            Period.THIS_MONTH -> cal.apply { set(Calendar.DAY_OF_MONTH, 1) }.time
            Period.LAST_30_DAYS -> cal.apply { add(Calendar.DAY_OF_YEAR, -29) }.time
            Period.THIS_YEAR -> cal.apply { set(Calendar.DAY_OF_YEAR, 1) }.time
        }
    }

    /** Groups transactions under month headers ("September 2026 · $124.50"). */
    private fun buildListItems(expenses: List<ExpenseTransaction>): List<ExpenseListItem> {
        val monthFormat = SimpleDateFormat("MMMM yyyy", Locale.getDefault())
        val items = mutableListOf<ExpenseListItem>()
        expenses.groupBy { e -> parsedDates[e.id]?.let { monthFormat.format(it) } ?: "Undated" }
            .forEach { (month, group) ->
                items += ExpenseListItem.Header(month, group.sumOf { it.amount })
                group.forEach { items += ExpenseListItem.Entry(it) }
            }
        return items
    }

    private fun renderSummary(expenses: List<ExpenseTransaction>) {
        val total = expenses.sumOf { it.amount }
        val count = expenses.size
        totalLabel.text = period.totalLabel
        totalSpend.text = formatMoney(total)
        statCount.text = count.toString()
        statAverage.text = formatMoney(if (count > 0) total / count else 0.0)
        statTopCategory.text = expenses
            .groupBy { it.category.expenseCategoryStyle().label }
            .maxByOrNull { (_, group) -> group.sumOf { it.amount } }
            ?.key ?: "-"

        val petLabel = selectedPetId?.let { id -> pets.firstOrNull { it.id == id }?.name } ?: "All pets"
        subtitle.text = "$petLabel · ${period.label}"
    }

    private fun renderBreakdown(expenses: List<ExpenseTransaction>) {
        val total = expenses.sumOf { it.amount }
        val totals = EXPENSE_CATEGORIES.associateWith { category ->
            expenses.filter { it.category.expenseCategoryStyle().label == category }.sumOf { it.amount }
        }

        categoryBar.removeAllViews()
        if (total <= 0.0) {
            categoryBar.addView(View(activity), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f))
        } else {
            totals.filterValues { it > 0 }.forEach { (category, value) ->
                categoryBar.addView(
                    View(activity).apply { setBackgroundColor(color(category.expenseCategoryStyle().colorRes)) },
                    LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, value.toFloat())
                )
            }
        }

        categoryLegend.removeAllViews()
        totals.forEach { (category, amount) -> categoryLegend.addView(createLegendRow(category, amount, total)) }
    }

    private fun createLegendRow(category: String, amount: Double, total: Double): View {
        val style = category.expenseCategoryStyle()
        val percent = if (total > 0) (amount / total * 100).roundToInt() else 0
        return LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 8.dp(), 0, 0)
            alpha = if (amount > 0) 1f else 0.55f

            addView(View(activity).apply {
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(color(style.colorRes))
                }
            }, LinearLayout.LayoutParams(10.dp(), 10.dp()))

            addView(TextView(activity).apply {
                text = style.label
                setTextColor(color(R.color.app_text_primary))
                textSize = 13f
                setPadding(10.dp(), 0, 0, 0)
            }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))

            addView(TextView(activity).apply {
                text = formatMoney(amount)
                setTextColor(color(R.color.app_text_primary))
                textSize = 13f
                typeface = activity.figtree(android.graphics.Typeface.BOLD)
            })

            addView(TextView(activity).apply {
                text = "$percent%"
                gravity = Gravity.END
                setTextColor(color(R.color.app_text_secondary))
                textSize = 12f
            }, LinearLayout.LayoutParams(44.dp(), LinearLayout.LayoutParams.WRAP_CONTENT))
        }
    }

    private fun rebuildPetChips() {
        petChips.removeAllViews()
        petChips.addView(createChip("All pets", null))
        pets.forEach { petChips.addView(createChip(it.name, it.id)) }
        (0 until petChips.childCount)
            .map { petChips.getChildAt(it) as Chip }
            .firstOrNull { it.tag == selectedPetId }
            ?.let { petChips.check(it.id) }
    }

    private fun createChip(label: String, tagValue: Any?): Chip = Chip(activity).apply {
        id = View.generateViewId()
        tag = tagValue
        text = label
        isCheckable = true
        isCheckedIconVisible = false
        chipMinHeight = 36f * density
        chipStrokeWidth = density
        setTextColor(ContextCompat.getColorStateList(activity, R.color.chip_selectable_text))
        chipBackgroundColor = ContextCompat.getColorStateList(activity, R.color.chip_selectable_bg)
        chipStrokeColor = ContextCompat.getColorStateList(activity, R.color.chip_selectable_stroke)
    }

    // endregion

    // region Delete

    private fun attachSwipeToDelete() {
        val callback = object : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT) {
            private val background = ColorDrawable(color(R.color.md_error))
            private val icon = ContextCompat.getDrawable(activity, R.drawable.ic_trash)?.mutate()?.apply {
                setTint(color(R.color.md_on_error))
            }

            override fun onMove(r: RecyclerView, v: RecyclerView.ViewHolder, t: RecyclerView.ViewHolder) = false

            override fun getSwipeDirs(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder): Int =
                if (viewHolder.itemViewType == ExpenseAdapter.TYPE_ENTRY) super.getSwipeDirs(recyclerView, viewHolder) else 0

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val position = viewHolder.bindingAdapterPosition
                val expense = adapter.expenseAt(position) ?: return
                confirmDelete(expense, position)
            }

            override fun onChildDraw(
                c: Canvas, rv: RecyclerView, vh: RecyclerView.ViewHolder,
                dX: Float, dY: Float, actionState: Int, isCurrentlyActive: Boolean
            ) {
                val item = vh.itemView
                if (dX < 0) {
                    // Stop short of the card's bottom margin so the red strip matches the card.
                    val bottom = item.bottom - 8.dp()
                    background.setBounds(item.right + dX.toInt(), item.top, item.right, bottom)
                    background.draw(c)
                    icon?.let {
                        val size = 22.dp()
                        val top = item.top + (bottom - item.top - size) / 2
                        val right = item.right - 20.dp()
                        it.setBounds(right - size, top, right, top + size)
                        it.draw(c)
                    }
                }
                super.onChildDraw(c, rv, vh, dX, dY, actionState, isCurrentlyActive)
            }
        }
        ItemTouchHelper(callback).attachToRecyclerView(recycler)
    }

    /** Asks before deleting; cancelling slides the swiped row back into place. */
    private fun confirmDelete(expense: ExpenseTransaction, position: Int) {
        val name = expense.description.ifBlank { "this expense" }
        var confirmed = false
        MaterialAlertDialogBuilder(activity)
            .setTitle("Delete expense?")
            .setMessage("Do you want to delete \"$name\" (${formatMoney(expense.amount)})? This can't be undone.")
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Delete") { _, _ ->
                confirmed = true
                delete(expense)
            }
            .setOnDismissListener { if (!confirmed) adapter.notifyItemChanged(position) }
            .show()
    }

    private fun delete(expense: ExpenseTransaction) {
        // Remove locally first so the row disappears immediately.
        allExpenses = allExpenses.filterNot { it.id == expense.id }
        applyFilters()

        runInBackground({ database.deleteExpense(expense.id) }) { deleted ->
            if (deleted) {
                showMessage("Expense deleted")
            } else {
                showMessage("Couldn't delete expense. Please try again.")
                refresh()
            }
        }
    }

    // endregion

    // region Export

    private fun showExportOptions() {
        if (visibleExpenses.isEmpty()) {
            showMessage("Nothing to download for this selection.")
            return
        }
        val options = arrayOf("PDF report", "CSV spreadsheet (Excel, Sheets)")
        MaterialAlertDialogBuilder(activity)
            .setTitle("Download ${visibleExpenses.size} expense${if (visibleExpenses.size == 1) "" else "s"}")
            .setItems(options) { _, which ->
                export(if (which == 0) ExpenseExporter.Format.PDF else ExpenseExporter.Format.CSV)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun export(format: ExpenseExporter.Format) {
        val report = ExpenseExporter.Report(
            expenses = visibleExpenses,
            scope = buildString {
                append(subtitle.text)
                if (searchQuery.isNotBlank()) append(" · matching \"$searchQuery\"")
            }
        )
        runInBackground({ ExpenseExporter(activity).export(report, format) }) { uri ->
            if (uri == null) {
                showMessage("Couldn't save the file. Please try again.")
                return@runInBackground
            }
            showMessage("Saved to Downloads/PetCare") {
                setAction("Open") { openExport(uri, format) }
            }
        }
    }

    private fun openExport(uri: Uri, format: ExpenseExporter.Format) {
        val view = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, format.mimeType)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        try {
            activity.startActivity(view)
        } catch (_: ActivityNotFoundException) {
            // No viewer installed for this type; let the user share it instead.
            val share = Intent(Intent.ACTION_SEND)
                .setType(format.mimeType)
                .putExtra(Intent.EXTRA_STREAM, uri)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            activity.startActivity(Intent.createChooser(share, "Share expenses"))
        }
    }

    // endregion

    // region Helpers

    private fun <T> runInBackground(work: () -> T, onResult: (T) -> Unit) {
        if (executor.isShutdown) return
        executor.execute {
            val result = work()
            activity.runOnUiThread {
                if (!activity.isFinishing && !activity.isDestroyed) onResult(result)
            }
        }
    }

    private fun showMessage(message: String, configure: Snackbar.() -> Unit = {}) {
        Snackbar.make(page, message, Snackbar.LENGTH_LONG)
            .setAnchorView(snackbarAnchor()?.takeIf { it.isShown })
            .apply(configure)
            .show()
    }

    private fun color(res: Int): Int = ContextCompat.getColor(activity, res)

    private fun Int.dp(): Int = (this * density).roundToInt()

    // endregion

    companion object {
        private const val SEARCH_DEBOUNCE_MS = 250L
    }
}
