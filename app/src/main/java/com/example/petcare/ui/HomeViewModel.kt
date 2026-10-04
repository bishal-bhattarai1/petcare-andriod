package com.example.petcare.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.petcare.CareTask
import com.example.petcare.PetDashboardModel
import com.example.petcare.data.ExpenseRepository
import com.example.petcare.data.PetRepository
import com.example.petcare.data.TaskRepository
import com.example.petcare.parseExpenseDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Calendar

/** Everything the Home tab shows, loaded together. */
data class HomeData(
    val pets: List<PetDashboardModel>,
    val tasks: List<CareTask>,
    val monthSpend: Double
)

/**
 * ViewModel for the Home tab. Loads pets, today's routines and this month's spending through the
 * repositories on a background thread and exposes them as a [StateFlow]. Because it survives
 * configuration changes (e.g. rotation), the last data is shown immediately instead of reloading.
 */
class HomeViewModel(application: Application) : AndroidViewModel(application) {
    private val pets = PetRepository(application)
    private val tasks = TaskRepository(application)
    private val expenses = ExpenseRepository(application)

    private val _data = MutableStateFlow<HomeData?>(null)

    /** null until the first load finishes. */
    val data: StateFlow<HomeData?> = _data.asStateFlow()

    /** Reloads from the database (called when the tab becomes visible). */
    fun refresh() {
        viewModelScope.launch(Dispatchers.IO) {
            _data.value = HomeData(pets.getAllPets(), tasks.getCareTasks(), monthSpend())
        }
    }

    private fun monthSpend(): Double {
        val monthStart = Calendar.getInstance().apply {
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.time
        return expenses.getExpenses()
            .filter { parseExpenseDate(it.date)?.let { d -> !d.before(monthStart) } ?: false }
            .sumOf { it.amount }
    }
}
