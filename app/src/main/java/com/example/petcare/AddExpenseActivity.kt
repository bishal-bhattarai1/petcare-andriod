package com.example.petcare

import android.app.DatePickerDialog
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.Button
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import java.util.Calendar
import java.util.Locale

class AddExpenseActivity : AppCompatActivity() {
    private lateinit var database: AuthDatabaseHelper
    private var selectedPet: PetOption? = null
    private var initialPetId: Long = -1L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_add_expense)
        database = AuthDatabaseHelper(this)
        initialPetId = intent.getLongExtra(EXTRA_SELECTED_PET_ID, -1L)

        updateStatusBarIcons()
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(android.R.id.content)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        findViewById<Toolbar>(R.id.toolbar).setNavigationOnClickListener { finish() }

        val saveButton = findViewById<Button>(R.id.buttonSaveExpense)
        val petOptions = setupPetPicker()
        setupDatePicker()

        if (petOptions.isEmpty()) {
            saveButton.isEnabled = false
            saveButton.alpha = 0.55f
        }

        saveButton.setOnClickListener {
            val pet = selectedPet
            if (pet == null) {
                Toast.makeText(this, "Please choose a pet", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val description = findViewById<TextInputEditText>(R.id.inputDescription)
                .text?.toString()?.trim().orEmpty()
            val amount = findViewById<TextInputEditText>(R.id.inputAmount)
                .text?.toString()?.trim()?.toDoubleOrNull() ?: 0.0
            val date = findViewById<TextInputEditText>(R.id.inputExpenseDate)
                .text?.toString()?.trim().orEmpty()

            if (description.isBlank()) {
                Toast.makeText(this, "Please add a description", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (amount <= 0.0) {
                Toast.makeText(this, "Please enter an amount", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val saved = database.saveExpense(
                petId = pet.id,
                category = selectedChipText(findViewById(R.id.chipGroupExpenseCategory), "Food"),
                description = description,
                date = date,
                amount = amount
            )

            if (saved) {
                Toast.makeText(this, "Expense saved!", Toast.LENGTH_SHORT).show()
                finish()
            } else {
                Toast.makeText(this, "Could not save expense", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun setupPetPicker(): List<PetOption> {
        val pets = database.getPetOptions()
        val input = findViewById<AutoCompleteTextView>(R.id.inputPetName)

        if (pets.isEmpty()) {
            input.setText("No pets added", false)
            input.isEnabled = false
            return pets
        }

        input.setAdapter(
            ArrayAdapter(
                this,
                android.R.layout.simple_dropdown_item_1line,
                pets.map { it.name }
            )
        )
        input.threshold = 0

        fun selectPet(pet: PetOption) {
            selectedPet = pet
            input.setText(pet.name, false)
        }

        selectPet(pets.firstOrNull { it.id == initialPetId } ?: pets.first())
        input.setOnClickListener { input.showDropDown() }
        input.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) input.showDropDown()
        }
        input.setOnItemClickListener { _, _, position, _ ->
            selectPet(pets[position])
        }

        return pets
    }

    private fun setupDatePicker() {
        val input = findViewById<TextInputEditText>(R.id.inputExpenseDate)
        val layout = findViewById<TextInputLayout>(R.id.layoutDate)
        val calendar = Calendar.getInstance()

        fun setDate(year: Int, month: Int, dayOfMonth: Int) {
            input.setText(String.format(Locale.getDefault(), "%02d/%02d/%d", dayOfMonth, month + 1, year))
        }

        setDate(
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        )

        val openPicker = View.OnClickListener {
            DatePickerDialog(
                this,
                { _, year, month, dayOfMonth -> setDate(year, month, dayOfMonth) },
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH),
                calendar.get(Calendar.DAY_OF_MONTH)
            ).show()
        }
        input.setOnClickListener(openPicker)
        layout.setEndIconOnClickListener(openPicker)
    }

    private fun selectedChipText(group: ChipGroup, fallback: String): String {
        val chipId = group.checkedChipId
        if (chipId == View.NO_ID) return fallback
        return findViewById<Chip>(chipId).text?.toString().orEmpty().ifBlank { fallback }
    }

    private fun updateStatusBarIcons() {
        val isDarkMode = (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES
        WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightStatusBars = !isDarkMode
    }

    companion object {
        const val EXTRA_SELECTED_PET_ID = "extra_selected_pet_id"
    }
}
