package com.example.petcare

import android.content.Intent
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity

class ShareReceiverActivity : AppCompatActivity() {

    private lateinit var database: AuthDatabaseHelper

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        database = AuthDatabaseHelper(this)

        if (Intent.ACTION_SEND == intent.action && "text/plain" == intent.type) {
            handleSharedText(intent.getStringExtra(Intent.EXTRA_TEXT))
        } else {
            finish()
        }
    }

    private fun handleSharedText(sharedText: String?) {
        if (sharedText == null) {
            finish()
            return
        }

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

        com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
            .setTitle("Save to PetCare Checklist")
            .setMessage("Save this shared info as a healthcare task?\n\n\"$sharedText\"")
            .setView(form)
            .setPositiveButton("Save") { _, _ ->
                val selectedName = petPicker.text.toString()
                val pet = pets.find { it.name == selectedName }
                if (pet != null) {
                    val savedId = database.saveTask(
                        petId = pet.id,
                        description = sharedText,
                        expenseAmount = 0.0,
                        category = "Healthcare",
                        repeatType = "Daily",
                        scheduledTime = "",
                        endsOn = "",
                        delegate = false,
                        reminder = true
                    )
                    if (savedId != -1L) {
                        NotificationHelper(this@ShareReceiverActivity).showTaskNotification(
                            savedId,
                            "Appointment Shared!",
                            "Saved to ${pet.name}'s checklist."
                        )
                        Toast.makeText(this, "Added to ${pet.name}'s checklist!", Toast.LENGTH_SHORT).show()
                    }
                }
                finish()
            }
            .setNegativeButton("Cancel") { _, _ -> finish() }
            .setOnCancelListener { finish() }
            .show()
    }
}
