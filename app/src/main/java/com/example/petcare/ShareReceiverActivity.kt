package com.example.petcare

import androidx.lifecycle.lifecycleScope
import com.example.petcare.data.HealthRepository
import com.example.petcare.data.PetRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.app.DatePickerDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Receives appointment details from other apps and saves them as a one-time health record:
 *  - text shared from SMS, email or a clinic's app ("Share → PetCare"),
 *  - links like petcare://import?desc=Rabies%20booster&date=2026-10-12&time=09:30%20AM.
 * The type, date and time are detected from the text and can be corrected before saving.
 */
class ShareReceiverActivity : AppCompatActivity() {

    private lateinit var pets: PetRepository
    private lateinit var health: HealthRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pets = PetRepository(this)
        health = HealthRepository(this)

        when {
            intent.action == Intent.ACTION_SEND && intent.type == "text/plain" ->
                intent.getStringExtra(Intent.EXTRA_TEXT)?.let { text ->
                    showImportDialog(text, detectTime(text), detectDate(text))
                } ?: finish()
            intent.action == Intent.ACTION_VIEW && intent.data != null -> handleDeepLink(intent.data!!)
            else -> finish()
        }
    }

    private fun handleDeepLink(data: Uri) {
        val desc = data.getQueryParameter("desc") ?: "Shared appointment"
        val time = data.getQueryParameter("time").orEmpty()
        val date = data.getQueryParameter("date")?.let { detectDate(it) }
        showImportDialog(desc, time, date)
    }

    /** Finds times like "10:00 AM", "2:30pm" or "14:00". */
    private fun detectTime(text: String): String =
        Regex("\\b\\d{1,2}:\\d{2}\\s*(?i:AM|PM)?\\b|\\b\\d{1,2}\\s*(?i:AM|PM)\\b").find(text)?.value?.trim().orEmpty()

    /** Finds dates like "12/10/2026" or "2026-10-12". */
    private fun detectDate(text: String): Calendar? {
        val candidates = listOf(
            Regex("\\b\\d{4}-\\d{2}-\\d{2}\\b") to "yyyy-MM-dd",
            Regex("\\b\\d{1,2}/\\d{1,2}/\\d{4}\\b") to "d/M/yyyy"
        )
        for ((regex, pattern) in candidates) {
            val match = regex.find(text)?.value ?: continue
            try {
                val date = SimpleDateFormat(pattern, Locale.US).apply { isLenient = false }.parse(match) ?: continue
                return Calendar.getInstance().apply { time = date }
            } catch (_: Exception) {
                // not a real date, keep looking
            }
        }
        return null
    }

    /** Loads the user's pets in the background, then asks which pet the shared text belongs to. */
    private fun showImportDialog(description: String, detectedTime: String, detectedDate: Calendar?) {
        lifecycleScope.launch {
            val petOptions = withContext(Dispatchers.IO) { pets.getPetOptions() }
            showImportDialog(description, detectedTime, detectedDate, petOptions)
        }
    }

    private fun showImportDialog(description: String, detectedTime: String, detectedDate: Calendar?, pets: List<PetOption>) {
        if (pets.isEmpty()) {
            Toast.makeText(this, "Add a pet in PetCare first to save this", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        val content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

        // Which pet
        content.addView(TextView(this).apply {
            text = "Pet"
            setTextColor(ContextCompat.getColor(this@ShareReceiverActivity, R.color.app_text_secondary))
            textSize = 12f
            setPadding(24.dp(), 8.dp(), 24.dp(), 0)
        })
        val petGroup = ChipGroup(this).apply {
            isSingleSelection = true
            isSelectionRequired = true
            setPadding(24.dp(), 4.dp(), 24.dp(), 0)
        }
        pets.forEachIndexed { index, pet -> petGroup.addView(choiceChip(pet.name, pet.id, index == 0)) }
        content.addView(petGroup)

        // Type, date and notes reuse the health-record form.
        val form = LayoutInflater.from(this).inflate(R.layout.dialog_health_record, content, false)
        content.addView(form)
        val typeGroup = form.findViewById<ChipGroup>(R.id.chipGroupRecordType)
        val dateLayout = form.findViewById<TextInputLayout>(R.id.layoutRecordDate)
        val dateInput = form.findViewById<TextInputEditText>(R.id.inputRecordDate)
        val notesInput = form.findViewById<TextInputEditText>(R.id.inputRecordNotes)

        val detectedType = RecordImporter.typeFor(description)
        RecordImporter.TYPES.forEach { type -> typeGroup.addView(choiceChip(type, type, type == detectedType)) }

        val picked = detectedDate ?: Calendar.getInstance()
        fun showDate() = dateInput.setText(SimpleDateFormat("d MMM yyyy", Locale.getDefault()).format(picked.time))
        showDate()
        val openPicker = View.OnClickListener {
            DatePickerDialog(this, { _, y, m, d ->
                picked.set(y, m, d)
                showDate()
            }, picked.get(Calendar.YEAR), picked.get(Calendar.MONTH), picked.get(Calendar.DAY_OF_MONTH)).show()
        }
        dateInput.setOnClickListener(openPicker)
        dateLayout.setEndIconOnClickListener(openPicker)

        notesInput.setText(listOf(detectedTime, description.trim()).filter { it.isNotBlank() }.joinToString(" · ").take(300))

        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle("Save to PetCare")
            .setView(content)
            .setNegativeButton("Cancel") { _, _ -> finish() }
            .setPositiveButton("Save", null)
            .setOnCancelListener { finish() }
            .show()

        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val petId = petGroup.findViewById<Chip>(petGroup.checkedChipId)?.tag as? Long ?: return@setOnClickListener
            val petName = pets.first { it.id == petId }.name
            val type = typeGroup.findViewById<Chip>(typeGroup.checkedChipId)?.tag as? String ?: "Other"
            val date = SimpleDateFormat("dd/MM/yyyy", Locale.US).format(picked.time)
            val notes = notesInput.text?.toString()?.trim().orEmpty()

            it.isEnabled = false
            lifecycleScope.launch {
                val saved = withContext(Dispatchers.IO) {
                    val recordId = health.addHealthcareRecord(petId, type, date, notes)
                    // Updates the pet's schedule: next vaccination date and/or a reminder on the day.
                    if (recordId != -1L) HealthSchedule.onRecordAdded(this@ShareReceiverActivity, petId, recordId, type, date)
                    recordId != -1L
                }
                val message = if (saved) "$type saved to $petName's health records" else "Couldn't save. Please try again."
                Toast.makeText(this@ShareReceiverActivity, message, Toast.LENGTH_SHORT).show()
                dialog.dismiss()
                finish()
            }
        }
    }

    private fun choiceChip(label: String, value: Any, checked: Boolean) = Chip(this).apply {
        id = View.generateViewId()
        tag = value
        text = label
        isCheckable = true
        isCheckedIconVisible = false
        isChecked = checked
        chipStrokeWidth = resources.displayMetrics.density
        setTextColor(ContextCompat.getColorStateList(this@ShareReceiverActivity, R.color.chip_selectable_text))
        chipBackgroundColor = ContextCompat.getColorStateList(this@ShareReceiverActivity, R.color.chip_selectable_bg)
        chipStrokeColor = ContextCompat.getColorStateList(this@ShareReceiverActivity, R.color.chip_selectable_stroke)
    }

    private fun Int.dp(): Int = (this * resources.displayMetrics.density).roundToInt()
}
