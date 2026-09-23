package com.example.petcare

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import java.text.DecimalFormat

class ExpenseAdapter(private val expenses: List<ExpenseTransaction>) :
    RecyclerView.Adapter<ExpenseAdapter.ExpenseViewHolder>() {

    class ExpenseViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val icon: ImageView = view.findViewById(R.id.imageCategoryIcon)
        val description: TextView = view.findViewById(R.id.textExpenseDescription)
        val meta: TextView = view.findViewById(R.id.textExpenseMeta)
        val amount: TextView = view.findViewById(R.id.textExpenseAmount)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ExpenseViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_expense_transaction, parent, false)
        return ExpenseViewHolder(view)
    }

    override fun onBindViewHolder(holder: ExpenseViewHolder, position: Int) {
        val expense = expenses[position]
        val context = holder.itemView.context
        val categoryStyle = expense.category.expenseCategoryStyle()

        holder.icon.setImageResource(categoryStyle.iconRes)
        holder.icon.setColorFilter(ContextCompat.getColor(context, categoryStyle.colorRes))
        holder.description.text = expense.description
        holder.meta.text = "${expense.petName} • ${expense.category} • ${expense.date}"
        
        val format = DecimalFormat("$#,##0.00")
        holder.amount.text = format.format(expense.amount)
    }

    override fun getItemCount() = expenses.size

    fun getExpenseAt(position: Int): ExpenseTransaction = expenses[position]
}

data class ExpenseCategoryStyle(
    val label: String,
    val colorRes: Int,
    val iconRes: Int
)

fun String.expenseCategoryStyle(): ExpenseCategoryStyle {
    return when (lowercase()) {
        "vet" -> ExpenseCategoryStyle("Vet", R.color.app_accent_red, R.drawable.ic_status_check)
        "grooming" -> ExpenseCategoryStyle("Grooming", R.color.indicator_dark, R.drawable.ic_groom)
        "toys" -> ExpenseCategoryStyle("Toys", R.color.app_text_secondary, R.drawable.ic_paw)
        else -> ExpenseCategoryStyle("Food", R.color.status_green, R.drawable.ic_bowl)
    }
}
