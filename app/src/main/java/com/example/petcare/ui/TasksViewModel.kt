package com.example.petcare.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.petcare.CareTask
import com.example.petcare.PetOption
import com.example.petcare.data.PetRepository
import com.example.petcare.data.TaskRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** What the Care Tasks page shows: the pet filter chips and the routines for the chosen day. */
data class TasksState(
    val pets: List<PetOption>,
    val tasks: List<CareTask>,
    /** The pet filter these tasks were loaded for (null = all pets). */
    val petId: Long?,
    val dateKey: String
)

/**
 * ViewModel for the Care Tasks page (Tasks tab and [com.example.petcare.TasksActivity]).
 * Loads pets and routines via the repositories on a background thread; write actions are
 * suspend functions that run on Dispatchers.IO and report success to the page.
 */
class TasksViewModel(application: Application) : AndroidViewModel(application) {
    private val pets = PetRepository(application)
    private val tasks = TaskRepository(application)

    private val _state = MutableStateFlow<TasksState?>(null)
    val state: StateFlow<TasksState?> = _state.asStateFlow()

    private var loadJob: Job? = null

    /** Loads routines for [petId] (null = all pets) on [dateKey]. A newer load cancels an older one. */
    fun load(petId: Long?, dateKey: String) {
        loadJob?.cancel()
        loadJob = viewModelScope.launch(Dispatchers.IO) {
            val petOptions = pets.getPetOptions()
            // A filter on a pet that was deleted falls back to "All pets".
            val filter = petId?.takeIf { id -> petOptions.any { it.id == id } }
            _state.value = TasksState(petOptions, tasks.getCareTasks(filter, dateKey), filter, dateKey)
        }
    }

    suspend fun completeTask(taskId: Long, dateKey: String): Boolean =
        withContext(Dispatchers.IO) { tasks.updateTaskCompletion(taskId, true, dateKey) }

    suspend fun deleteTask(taskId: Long): Boolean =
        withContext(Dispatchers.IO) { tasks.deleteTask(taskId) }

    suspend fun updateTaskDetails(taskId: Long, description: String, time: String, supplies: String, notes: String): Boolean =
        withContext(Dispatchers.IO) { tasks.updateTaskDetails(taskId, description, time, supplies, notes) }
}
