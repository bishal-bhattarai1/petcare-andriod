package com.example.petcare

import android.content.Intent
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class ShareReceiverActivity : AppCompatActivity() {

    private lateinit var database: AuthDatabaseHelper

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        database = AuthDatabaseHelper(this)

        val action = intent.action
        val type = intent.type

        if (Intent.ACTION_SEND == action && "text/plain" == type) {
            handleSharedText(intent.getStringExtra(Intent.EXTRA_TEXT))
        } else if (Intent.ACTION_VIEW == action) {
            handleDeepLink(intent.data)
        } else {
            finish()
        }
    }

    private fun handleDeepLink(data: android.net.Uri?) {
        if (data == null) {
            finish()
            return
        }
        val desc = data.getQueryParameter("desc") ?: "Shared Appointment"
        val time = data.getQueryParameter("time") ?: ""
        showImportDialog(desc, time)
    }

    private fun handleSharedText(sharedText: String?) {
        if (sharedText == null) {
            finish()
            return
        }
        
        val detectedTime = extractTimeFromText(sharedText)
        showImportDialog(sharedText, detectedTime)
    }

    private fun extractTimeFromText(text: String): String {
        // Regex to find patterns like 10:00 AM, 2:30PM, 14:00
        val regex = Regex("(\\d{1,2}:\\d{2}\\s*(?i)(AM|PM)?|\\d{1,2}\\s*(?i)(AM|PM))")
        val match = regex.find(text)
        return match?.value ?: ""
    }

    private fun showImportDialog(description: String, initialTime: String) {
        val pets = database.getPetOptions()
        if (pets.isEmpty()) {
            Toast.makeText(this, "Add a pet in PetCare first to save this info", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        val form = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 32, 48, 0)
        }
        
        val petPicker = AutoCompleteTextView(this).apply {
            hint = "Pick a pet"
            setAdapter(ArrayAdapter(this@ShareReceiverActivity, android.R.layout.simple_dropdown_item_1line, pets.map { it.name }))
            setOnClickListener { showDropDown() }
        }
        form.addView(petPicker)

        val timeInput = android.widget.EditText(this).apply {
            hint = "Scheduled Time (Optional)"
            setText(initialTime)
            setPadding(0, 16, 0, 16)
        }
        if (initialTime.isNotBlank()) {
            form.addView(TextView(this).apply { 
                text = "Detected Time: $initialTime"
                textSize = 12f
                setTextColor(resources.getColor(R.color.status_green, null))
                setPadding(0, 16, 0, 0)
            })
        }
        form.addView(timeInput)

        com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
            .setTitle("Import to PetCare")
            .setMessage("We detected a potential appointment or medical detail:\n\n\"$description\"\n\nWould you like to save this as a high-priority healthcare task?")
            .setView(form)
            .setPositiveButton("Save Routine") { _, _ ->
                val selectedName = petPicker.text.toString()
                val pet = pets.find { it.name == selectedName }
                val finalTime = timeInput.text.toString().trim()
                
                if (pet != null) {
                    val savedId = database.saveTask(
                        petId = pet.id,
                        description = description,
                        expenseAmount = 0.0,
                        category = "Healthcare",
                        repeatType = "Daily",
                        scheduledTime = finalTime,
                        endsOn = "",
                        delegate = false,
                        reminder = true,
                        notes = "Imported from external share"
                    )
                    if (savedId != -1L) {
                        NotificationHelper(this@ShareReceiverActivity).showTaskNotification(
                            savedId,
                            "${pet.name}: Healthcare Imported",
                            "New routine added to your checklist."
                        )
                        Toast.makeText(this, "Imported to ${pet.name}'s checklist!", Toast.LENGTH_SHORT).show()
                    }
                }
                finish()
            }
            .setNegativeButton("Cancel") { _, _ -> finish() }
            .setOnCancelListener { finish() }
            .show()
    }
}
