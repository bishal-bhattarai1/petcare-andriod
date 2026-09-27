package com.example.petcare

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import java.util.Calendar
import java.util.Locale

class AddTaskActivity : AppCompatActivity() {
    private lateinit var database: AuthDatabaseHelper
    private lateinit var weeklyDaysLayout: LinearLayout
    private val selectedWeekDays = linkedSetOf("Mon")
    private var selectedPet: PetOption? = null
    private var selectedLocationId: Long = -1L
    private var initialPetId: Long = -1L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_add_task)
        database = AuthDatabaseHelper(this)
        initialPetId = intent.getLongExtra(EXTRA_SELECTED_PET_ID, -1L)

        updateStatusBarIcons()

        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        toolbar.setNavigationOnClickListener { finish() }

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(android.R.id.content)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        val descInput = findViewById<EditText>(R.id.editTextDesc)
        val expenseInput = findViewById<EditText>(R.id.editTextExpense)
        val saveButton = findViewById<Button>(R.id.buttonSaveTask)
        val categoryGroup = findViewById<ChipGroup>(R.id.chipGroupCategory)
        val repeatGroup = findViewById<ChipGroup>(R.id.chipGroupRepeat)
        
        setupPetPicker()
        setupLocationPicker()
        setupScheduleControls()

        saveButton.setOnClickListener {
            val pet = selectedPet
            if (pet == null) {
                Toast.makeText(this, "Please choose a pet", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val desc = descInput.text.toString().trim()
            if (desc.isEmpty()) {
                Toast.makeText(this, "Please describe the task", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val expense = expenseInput.text.toString().trim().toDoubleOrNull() ?: 0.0
            val repeatType = when (repeatGroup.checkedChipId) {
                R.id.chipWeekly -> "Weekly"
                R.id.chipMonthly -> "Monthly"
                else -> "Daily"
            }
            val monthDay = findViewById<TextInputEditText>(R.id.inputMonthDay).text?.toString()?.trim()?.toIntOrNull()
            if (repeatType == "Monthly" && (monthDay == null || monthDay !in 1..31)) {
                findViewById<TextInputLayout>(R.id.layoutMonthDay).error = "Enter a day from 1 to 31"
                return@setOnClickListener
            }
            val weekDays = when (repeatType) {
                "Weekly" -> selectedWeekDays.joinToString(",")
                "Monthly" -> monthDay.toString() // Day of the month, e.g. "15".
                else -> ""
            }
            val scheduledTime = findViewById<TextInputEditText>(R.id.inputTaskTime).text?.toString().orEmpty()
            val reminderEnabled = findViewById<android.widget.CheckBox>(R.id.checkReminder).isChecked
            val supplies = findViewById<TextInputEditText>(R.id.inputSupplies).text?.toString().orEmpty()
            val notes = findViewById<TextInputEditText>(R.id.inputTaskNotes).text?.toString().orEmpty()
            
            val taskId = database.saveTask(
                petId = pet.id,
                description = desc,
                expenseAmount = expense,
                category = selectedChipText(categoryGroup, "Feeding"),
                repeatType = repeatType,
                weekDays = weekDays,
                scheduledTime = scheduledTime,
                endsOn = findViewById<TextInputEditText>(R.id.inputEndsOn).text?.toString().orEmpty(),
                delegate = findViewById<android.widget.CheckBox>(R.id.checkDelegate).isChecked,
                reminder = reminderEnabled,
                supplies = supplies,
                notes = notes,
                locationId = selectedLocationId
            )

            if (taskId != -1L) {
                if (reminderEnabled && scheduledTime.isNotBlank()) {
                    database.getCareTasks(pet.id).find { it.id == taskId }?.let { TaskReminder.schedule(this, it) }
                }
                Toast.makeText(this, "Care routine saved!", Toast.LENGTH_SHORT).show()
                finish()
            } else {
                Toast.makeText(this, "Could not save care routine", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun setupPetPicker() {
        val pets = database.getPetOptions()
        val subtitle = findViewById<TextView>(R.id.textPetSubtitle)
        val input = findViewById<AutoCompleteTextView>(R.id.inputPetName)

        if (pets.isEmpty()) {
            input.setText("No pets added", false)
            input.isEnabled = false
            subtitle.text = "Add a pet first"
            return
        }

        val adapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, pets.map { it.name })
        input.setAdapter(adapter)
        input.threshold = 0

        fun selectPet(pet: PetOption) {
            selectedPet = pet
            input.setText(pet.name, false)
            subtitle.text = "For ${pet.name}"
        }

        selectPet(pets.firstOrNull { it.id == initialPetId } ?: pets.first())
        input.setOnClickListener { input.showDropDown() }
        input.setOnItemClickListener { _, _, position, _ -> selectPet(pets[position]) }
    }

    override fun onResume() {
        super.onResume()
        setupLocationPicker()
    }

    private fun setupLocationPicker() {
        val locations = database.getLocations()
        val input = findViewById<AutoCompleteTextView>(R.id.inputLinkLocation)
        
        if (locations.isEmpty()) {
            input.setText("None / No saved locations", false)
            input.setAdapter(null)
            input.setOnClickListener {
                Toast.makeText(this, "No saved locations yet. Save locations in the Locations screen!", Toast.LENGTH_SHORT).show()
            }
            selectedLocationId = -1L
            return
        }

        val names = locations.map { if (it.category.isNotBlank()) "${it.name} (${it.category})" else it.name }.toMutableList()
        names.add(0, "None / No Location")
        
        val adapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, names)
        input.setAdapter(adapter)
        
        if (selectedLocationId == -1L) {
            input.setText(names[0], false)
        } else {
            val idx = locations.indexOfFirst { it.id == selectedLocationId }
            if (idx != -1) {
                input.setText(names[idx + 1], false)
            } else {
                input.setText(names[0], false)
                selectedLocationId = -1L
            }
        }

        input.setOnClickListener { input.showDropDown() }
        input.setOnItemClickListener { _, _, position, _ ->
            selectedLocationId = if (position == 0) -1L else locations[position - 1].id
        }
    }

    private fun setupScheduleControls() {
        weeklyDaysLayout = findViewById(R.id.layoutWeeklyDays)
        weeklyDaysLayout.visibility = View.GONE

        val repeatGroup = findViewById<ChipGroup>(R.id.chipGroupRepeat)
        val monthDayLayout = findViewById<TextInputLayout>(R.id.layoutMonthDay)
        findViewById<TextInputEditText>(R.id.inputMonthDay).setText(Calendar.getInstance().get(Calendar.DAY_OF_MONTH).toString())
        repeatGroup.setOnCheckedStateChangeListener { _, checkedIds ->
            weeklyDaysLayout.visibility = if (checkedIds.contains(R.id.chipWeekly)) View.VISIBLE else View.GONE
            monthDayLayout.visibility = if (checkedIds.contains(R.id.chipMonthly)) View.VISIBLE else View.GONE
            monthDayLayout.error = null
        }

        setupTimePicker()
        setupDatePicker()
        setupWeekDay(R.id.dayMonday, "Mon")
        setupWeekDay(R.id.dayTuesday, "Tue")
        setupWeekDay(R.id.dayWednesday, "Wed")
        setupWeekDay(R.id.dayThursday, "Thu")
        setupWeekDay(R.id.dayFriday, "Fri")
        setupWeekDay(R.id.daySaturday, "Sat")
        setupWeekDay(R.id.daySunday, "Sun")
        refreshWeekDays()
    }

    private fun setupTimePicker() {
        val input = findViewById<TextInputEditText>(R.id.inputTaskTime)
        val layout = findViewById<TextInputLayout>(R.id.layoutTime)
        val openPicker = View.OnClickListener { showTimePicker(input) }
        input.setOnClickListener(openPicker)
        layout.setEndIconOnClickListener(openPicker)
    }

    private fun setupDatePicker() {
        val input = findViewById<TextInputEditText>(R.id.inputEndsOn)
        val layout = findViewById<TextInputLayout>(R.id.layoutEndsOn)
        val openPicker = View.OnClickListener { showEndsOnPicker(input) }
        input.setOnClickListener(openPicker)
        layout.setEndIconOnClickListener(openPicker)
    }

    private fun showTimePicker(input: TextInputEditText) {
        val calendar = Calendar.getInstance()
        TimePickerDialog(this, { _, h, m ->
            val amPm = if (h >= 12) "PM" else "AM"
            val hour = if (h == 0 || h == 12) 12 else h % 12
            input.setText(String.format(Locale.getDefault(), "%02d:%02d %s", hour, m, amPm))
        }, calendar.get(Calendar.HOUR_OF_DAY), calendar.get(Calendar.MINUTE), false).show()
    }

    private fun showEndsOnPicker(input: TextInputEditText) {
        val calendar = Calendar.getInstance()
        DatePickerDialog(this, { _, y, m, d ->
            input.setText(String.format(Locale.getDefault(), "%02d/%02d/%d", d, m + 1, y))
        }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH)).show()
    }

    private fun setupWeekDay(viewId: Int, dayCode: String) {
        findViewById<TextView>(viewId).setOnClickListener {
            if (selectedWeekDays.contains(dayCode) && selectedWeekDays.size > 1) {
                selectedWeekDays.remove(dayCode)
            } else {
                selectedWeekDays.add(dayCode)
            }
            refreshWeekDays()
        }
    }

    private fun refreshWeekDays() {
        setWeekDaySelected(R.id.dayMonday, selectedWeekDays.contains("Mon"))
        setWeekDaySelected(R.id.dayTuesday, selectedWeekDays.contains("Tue"))
        setWeekDaySelected(R.id.dayWednesday, selectedWeekDays.contains("Wed"))
        setWeekDaySelected(R.id.dayThursday, selectedWeekDays.contains("Thu"))
        setWeekDaySelected(R.id.dayFriday, selectedWeekDays.contains("Fri"))
        setWeekDaySelected(R.id.daySaturday, selectedWeekDays.contains("Sat"))
        setWeekDaySelected(R.id.daySunday, selectedWeekDays.contains("Sun"))
    }

    private fun setWeekDaySelected(viewId: Int, selected: Boolean) {
        val view = findViewById<TextView>(viewId)
        view.setBackgroundResource(if (selected) R.drawable.bg_day_selected else R.drawable.bg_day_unselected)
        view.setTextColor(ContextCompat.getColor(this, if (selected) R.color.white else R.color.app_text_secondary))
    }

    private fun selectedChipText(group: ChipGroup, fallback: String): String {
        val chipId = group.checkedChipId
        if (chipId == View.NO_ID) return fallback
        return findViewById<Chip>(chipId).text?.toString() ?: fallback
    }

    private fun updateStatusBarIcons() {
        val isDarkMode = (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES
        WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightStatusBars = !isDarkMode
    }

    companion object {
        const val EXTRA_SELECTED_PET_ID = "extra_selected_pet_id"
    }
}
