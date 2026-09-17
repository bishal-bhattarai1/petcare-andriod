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
import com.example.petcare.data.SessionManager
import com.example.petcare.data.TaskEntity
import com.google.android.material.textfield.TextInputEditText
import java.util.Locale

class AddEditTaskActivity : AppCompatActivity() {
    private lateinit var database: PetCareDatabase
    private lateinit var sessionManager: SessionManager
    private var taskId: Int = -1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_add_edit_task)
        
        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        toolbar.setNavigationOnClickListener { finish() }

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main_add_edit_task)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        database = PetCareDatabase.getDatabase(this)
        sessionManager = SessionManager(this)

        val editTextPetName = findViewById<TextInputEditText>(R.id.editTextPetName)
        val editTextDescription = findViewById<TextInputEditText>(R.id.editTextDescription)
        val editTextFeeding = findViewById<TextInputEditText>(R.id.editTextFeeding)
        val editTextMedication = findViewById<TextInputEditText>(R.id.editTextMedication)
        val editTextGrooming = findViewById<TextInputEditText>(R.id.editTextGrooming)
        val editTextAppointment = findViewById<TextInputEditText>(R.id.editTextAppointment)
        val buttonSaveTask = findViewById<Button>(R.id.buttonSaveTask)

        editTextAppointment.setOnClickListener {
            val calendar = java.util.Calendar.getInstance()
            val year = calendar.get(java.util.Calendar.YEAR)
            val month = calendar.get(java.util.Calendar.MONTH)
            val day = calendar.get(java.util.Calendar.DAY_OF_MONTH)

            val datePickerDialog = android.app.DatePickerDialog(this, { _, selectedYear, selectedMonth, selectedDay ->
                val formattedDate = String.format(Locale.ROOT, "%04d-%02d-%02d", selectedYear, selectedMonth + 1, selectedDay)
                editTextAppointment.setText(formattedDate)
            }, year, month, day)
            datePickerDialog.show()
        }

        taskId = intent.getIntExtra("TASK_ID", -1)
        if (taskId != -1) {
            val task = database.taskDao().getTaskById(taskId)
            if (task != null) {
                editTextPetName.setText(task.petName)
                editTextDescription.setText(task.description)
                editTextFeeding.setText(task.feedingTime)
                editTextMedication.setText(task.medicationSchedule)
                editTextGrooming.setText(task.groomingInstructions)
                editTextAppointment.setText(task.appointmentDateTime)
            }
        }

        buttonSaveTask.setOnClickListener {
            val petName = editTextPetName.text.toString().trim()
            val description = editTextDescription.text.toString().trim()
            val feeding = editTextFeeding.text.toString().trim()
            val medication = editTextMedication.text.toString().trim()
            val grooming = editTextGrooming.text.toString().trim()
            val appointment = editTextAppointment.text.toString().trim()

            if (petName.isEmpty() || description.isEmpty()) {
                Toast.makeText(this, "Pet name and description are required", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val ownerEmail = sessionManager.getUserEmail()
            val task = TaskEntity(ownerEmail, petName, description, feeding, medication, grooming, appointment, false)
            if (taskId != -1) {
                task.id = taskId
                database.taskDao().updateTask(task)
                Toast.makeText(this, "Task updated successfully", Toast.LENGTH_SHORT).show()
            } else {
                database.taskDao().insertTask(task)
                Toast.makeText(this, "Task added successfully", Toast.LENGTH_SHORT).show()
            }
            finish()
        }
    }
}