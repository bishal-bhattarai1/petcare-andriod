package com.example.petcare.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.petcare.ExpenseTransaction
import com.example.petcare.PetOption
import com.example.petcare.data.ExpenseRepository
import com.example.petcare.data.PetRepository
import com.example.petcare.parseExpenseDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Date

/** The user's pets and all their expenses; filtering by pet/period/search happens on the page. */
data class ExpensesState(
    val pets: List<PetOption>,
    val expenses: List<ExpenseTransaction>,
    /** Expense id -> parsed date, computed once in the background for the period filters. */
    val parsedDates: Map<Long, Date?>
)

/**
 * ViewModel for the Spending page (Expenses tab and [com.example.petcare.ExpensesActivity]).
 * Database reads and deletes run on Dispatchers.IO through the repositories.
 */
class ExpensesViewModel(application: Application) : AndroidViewModel(application) {
    private val pets = PetRepository(application)
    private val expenses = ExpenseRepository(application)

    private val _state = MutableStateFlow<ExpensesState?>(null)
    val state: StateFlow<ExpensesState?> = _state.asStateFlow()

    /** Reloads pets and expenses (e.g. after returning from Add Expense). */
    fun refresh() {
        viewModelScope.launch(Dispatchers.IO) {
            val loaded = expenses.getExpenses()
            _state.value = ExpensesState(pets.getPetOptions(), loaded, loaded.associate { it.id to parseExpenseDate(it.date) })
        }
    }

    suspend fun deleteExpense(expenseId: Long): Boolean =
        withContext(Dispatchers.IO) { expenses.deleteExpense(expenseId) }
}
