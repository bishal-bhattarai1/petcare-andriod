package com.example.petcare

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.text.SimpleDateFormat
import java.util.Locale

/** A row in the expenses list: either a month header or a transaction. */
sealed class ExpenseListItem {
    data class Header(val month: String, val total: Double) : ExpenseListItem()
    data class Entry(val expense: ExpenseTransaction) : ExpenseListItem()
}

class ExpenseAdapter : ListAdapter<ExpenseListItem, RecyclerView.ViewHolder>(DIFF) {

    class EntryViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val iconBg: FrameLayout = view.findViewById(R.id.layoutCategoryIcon)
        val icon: ImageView = view.findViewById(R.id.imageCategoryIcon)
        val description: TextView = view.findViewById(R.id.textExpenseDescription)
        val meta: TextView = view.findViewById(R.id.textExpenseMeta)
        val amount: TextView = view.findViewById(R.id.textExpenseAmount)
    }

    class HeaderViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val month: TextView = view.findViewById(R.id.textHeaderMonth)
        val total: TextView = view.findViewById(R.id.textHeaderTotal)
    }

    override fun getItemViewType(position: Int): Int = when (getItem(position)) {
        is ExpenseListItem.Header -> TYPE_HEADER
        is ExpenseListItem.Entry -> TYPE_ENTRY
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == TYPE_HEADER) {
            HeaderViewHolder(inflater.inflate(R.layout.item_expense_header, parent, false))
        } else {
            EntryViewHolder(inflater.inflate(R.layout.item_expense_transaction, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = getItem(position)) {
            is ExpenseListItem.Header -> (holder as HeaderViewHolder).apply {
                month.text = item.month
                total.text = formatMoney(item.total)
            }
            is ExpenseListItem.Entry -> (holder as EntryViewHolder).bind(item.expense)
        }
    }

    private fun EntryViewHolder.bind(expense: ExpenseTransaction) {
        val context = itemView.context
        val style = expense.category.expenseCategoryStyle()
        icon.setImageResource(style.iconRes)
        icon.setColorFilter(ContextCompat.getColor(context, style.colorRes))
        iconBg.backgroundTintList = ContextCompat.getColorStateList(context, style.backgroundRes)
        description.text = expense.description.ifBlank { style.label }
        meta.text = listOf(expense.petName, style.label, displayDate(expense.date))
            .filter { it.isNotBlank() }
            .joinToString(" · ")
        amount.text = formatMoney(expense.amount)
        itemView.contentDescription = "${description.text}, ${amount.text}, ${meta.text}"
    }

    /** The transaction at [position], or null for a month header (headers can't be swiped). */
    fun expenseAt(position: Int): ExpenseTransaction? =
        (currentList.getOrNull(position) as? ExpenseListItem.Entry)?.expense

    companion object {
        const val TYPE_HEADER = 0
        const val TYPE_ENTRY = 1

        private val DIFF = object : DiffUtil.ItemCallback<ExpenseListItem>() {
            override fun areItemsTheSame(oldItem: ExpenseListItem, newItem: ExpenseListItem): Boolean = when {
                oldItem is ExpenseListItem.Header && newItem is ExpenseListItem.Header -> oldItem.month == newItem.month
                oldItem is ExpenseListItem.Entry && newItem is ExpenseListItem.Entry -> oldItem.expense.id == newItem.expense.id
                else -> false
            }

            override fun areContentsTheSame(oldItem: ExpenseListItem, newItem: ExpenseListItem) = oldItem == newItem
        }
    }
}

data class ExpenseCategoryStyle(
    val label: String,
    val colorRes: Int,
    val backgroundRes: Int,
    val iconRes: Int
)

/** The fixed set of expense categories, in display order. */
val EXPENSE_CATEGORIES = listOf("Food", "Vet", "Grooming", "Toys")

fun String.expenseCategoryStyle(): ExpenseCategoryStyle {
    return when (lowercase()) {
        "vet" -> ExpenseCategoryStyle("Vet", R.color.expense_vet, R.color.expense_vet_bg, R.drawable.ic_meds)
        "grooming" -> ExpenseCategoryStyle("Grooming", R.color.expense_grooming, R.color.expense_grooming_bg, R.drawable.ic_groom)
        "toys" -> ExpenseCategoryStyle("Toys", R.color.expense_toys, R.color.expense_toys_bg, R.drawable.ic_paw)
        else -> ExpenseCategoryStyle("Food", R.color.expense_food, R.color.expense_food_bg, R.drawable.ic_bowl)
    }
}

private val moneyFormat = ThreadLocal.withInitial { DecimalFormat("$#,##0.00", DecimalFormatSymbols(Locale.US)) }

fun formatMoney(amount: Double): String = moneyFormat.get()!!.format(amount)

/** Expense dates are stored as dd/MM/yyyy; show them as e.g. "19 Sep 2026". */
fun displayDate(stored: String): String {
    val parsed = parseExpenseDate(stored) ?: return stored
    return SimpleDateFormat("d MMM yyyy", Locale.getDefault()).format(parsed)
}

fun parseExpenseDate(stored: String): java.util.Date? {
    if (stored.isBlank()) return null
    return try {
        SimpleDateFormat("dd/MM/yyyy", Locale.US).apply { isLenient = false }.parse(stored.trim())
    } catch (_: Exception) {
        null
    }
}
