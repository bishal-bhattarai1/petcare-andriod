package com.example.petcare

import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.appcompat.widget.Toolbar
import com.example.petcare.data.PetCareDatabase
import com.example.petcare.data.PetEntity
import com.example.petcare.data.SessionManager
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.textfield.TextInputEditText
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AddEditPetActivity : AppCompatActivity() {
    private lateinit var database: PetCareDatabase
    private lateinit var sessionManager: SessionManager
    private var petId: Int = -1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_add_edit_pet)
        
        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        toolbar.setNavigationOnClickListener { finish() }

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main_add_edit_pet)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        database = PetCareDatabase.getDatabase(this)
        sessionManager = SessionManager(this)

        val editTextName = findViewById<TextInputEditText>(R.id.editTextName)
        val editTextBreed = findViewById<TextInputEditText>(R.id.editTextBreed)
        val editTextAge = findViewById<TextInputEditText>(R.id.editTextAge)
        val editTextWeight = findViewById<TextInputEditText>(R.id.editTextWeight)
        val editTextDiet = findViewById<TextInputEditText>(R.id.editTextDiet)
        val editTextAllergies = findViewById<TextInputEditText>(R.id.editTextAllergies)
        val editTextVaccination = findViewById<TextInputEditText>(R.id.editTextVaccination)
        val buttonSave = findViewById<Button>(R.id.buttonSave)

        editTextVaccination.setOnClickListener {
            val calendar = java.util.Calendar.getInstance()
            val year = calendar.get(java.util.Calendar.YEAR)
            val month = calendar.get(java.util.Calendar.MONTH)
            val day = calendar.get(java.util.Calendar.DAY_OF_MONTH)

            val datePickerDialog = android.app.DatePickerDialog(this, { _, selectedYear, selectedMonth, selectedDay ->
                val formattedDate = String.format(Locale.ROOT, "%04d-%02d-%02d", selectedYear, selectedMonth + 1, selectedDay)
                editTextVaccination.setText(formattedDate)
            }, year, month, day)
            datePickerDialog.show()
        }

        petId = intent.getIntExtra("PET_ID", -1)
        if (petId != -1) {
            val pet = database.petDao().getPetById(petId)
            if (pet != null) {
                editTextName.setText(pet.name)
                editTextBreed.setText(pet.breed)
                editTextAge.setText(pet.age)
                editTextWeight.setText(pet.weight)
                editTextDiet.setText(pet.diet)
                editTextAllergies.setText(pet.allergies)
                editTextVaccination.setText(pet.vaccinationHistory)
            }
        }

        buttonSave.setOnClickListener {
            val name = editTextName.text.toString().trim()
            val breed = editTextBreed.text.toString().trim()
            val age = editTextAge.text.toString().trim()
            val weight = editTextWeight.text.toString().trim()
            val diet = editTextDiet.text.toString().trim()
            val allergies = editTextAllergies.text.toString().trim()
            val vaccination = editTextVaccination.text.toString().trim()

            if (name.isEmpty()) {
                Toast.makeText(this, "Pet name is required", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val ownerEmail = sessionManager.getUserEmail()
            val pet = PetEntity(ownerEmail, name, breed, age, weight, diet, allergies, vaccination, "")
            if (petId != -1) {
                pet.id = petId
                database.petDao().updatePet(pet)
                Toast.makeText(this, "Pet updated successfully", Toast.LENGTH_SHORT).show()
            } else {
                database.petDao().insertPet(pet)
                Toast.makeText(this, "Pet added successfully", Toast.LENGTH_SHORT).show()
            }
            finish()
        }
    }
}