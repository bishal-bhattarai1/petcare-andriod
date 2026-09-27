package com.example.petcare

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.checkbox.MaterialCheckBox
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.math.roundToInt

/**
 * Imports vaccination schedules and appointments from a vet's calendar file (.ics) or a CSV
 * spreadsheet as health records. Opened from Profile, or when another app shares/opens such a file.
 */
class ImportRecordsActivity : AppCompatActivity() {

    private data class Candidate(val record: ImportedRecord, val checkBox: MaterialCheckBox)

    private lateinit var database: AuthDatabaseHelper
    private val executor: ExecutorService = Executors.newSingleThreadExecutor()
    private var parsed: List<ImportedRecord> = emptyList()
    private var candidates: List<Candidate> = emptyList()
    private var selectedPetId: Long = -1L

    private val pickFile = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { readFile(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_import_records)
        database = AuthDatabaseHelper(this)

        val isDark = (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
            android.content.res.Configuration.UI_MODE_NIGHT_YES
        WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightStatusBars = !isDark
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(android.R.id.content)) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
        findViewById<MaterialToolbar>(R.id.toolbar).setNavigationOnClickListener { finish() }
        findViewById<View>(R.id.buttonChooseFile).setOnClickListener {
            // Many apps label .ics/.csv files loosely, so accept plain text types too.
            pickFile.launch(arrayOf("text/calendar", "text/csv", "text/comma-separated-values", "text/plain", "application/octet-stream"))
        }
        findViewById<View>(R.id.buttonImport).setOnClickListener { importSelected() }

        // Opened from another app with a file (e.g. an .ics attachment).
        incomingUri(intent)?.let { readFile(it) }
    }

    override fun onDestroy() {
        executor.shutdownNow()
        super.onDestroy()
    }

    private fun incomingUri(intent: Intent): Uri? = when (intent.action) {
        Intent.ACTION_VIEW -> intent.data
        Intent.ACTION_SEND -> androidx.core.content.IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
        else -> null
    }

    // region Reading

    private fun readFile(uri: Uri) {
        runInBackground({
            val name = fileName(uri)
            val text = try {
                contentResolver.openInputStream(uri)?.use { stream ->
                    // Records files are small; cap the read so a wrong file can't exhaust memory.
                    val buffer = java.io.ByteArrayOutputStream()
                    val chunk = ByteArray(8192)
                    while (buffer.size() < MAX_FILE_BYTES) {
                        val read = stream.read(chunk, 0, minOf(chunk.size, MAX_FILE_BYTES - buffer.size()))
                        if (read <= 0) break
                        buffer.write(chunk, 0, read)
                    }
                    buffer.toString(Charsets.UTF_8.name())
                }
            } catch (_: Exception) {
                null
            }
            Triple(name, text?.let { RecordImporter.parse(it) }, database.getPetOptions())
        }) { (name, result, pets) ->
            val fileLabel = findViewById<TextView>(R.id.textFileName)
            fileLabel.visibility = View.VISIBLE
            when {
                result == null -> showError(fileLabel, name, "Couldn't read this file.")
                result.records.isEmpty() -> showError(
                    fileLabel, name,
                    "No dated records found. Use an .ics calendar file, or a .csv with a date column (e.g. 2026-10-12)."
                )
                pets.isEmpty() -> showError(fileLabel, name, "Add a pet first, then import its records.")
                else -> {
                    parsed = result.records
                    fileLabel.text = buildString {
                        append("$name · ${result.records.size} record${if (result.records.size == 1) "" else "s"} found")
                        if (result.skipped > 0) append(" · ${result.skipped} skipped (no valid date)")
                    }
                    showPets(pets)
                }
            }
        }
    }

    private fun showError(fileLabel: TextView, name: String, message: String) {
        fileLabel.text = name
        parsed = emptyList()
        findViewById<View>(R.id.layoutImportPreview).visibility = View.GONE
        findViewById<View>(R.id.layoutImportBar).visibility = View.GONE
        findViewById<TextView>(R.id.textImportMessage).apply {
            text = message
            visibility = View.VISIBLE
        }
    }

    private fun fileName(uri: Uri): String = try {
        contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
            if (it.moveToFirst()) it.getString(0) else null
        } ?: uri.lastPathSegment.orEmpty()
    } catch (_: Exception) {
        uri.lastPathSegment.orEmpty()
    }

    // endregion

    // region Preview

    private fun showPets(pets: List<PetOption>) {
        findViewById<View>(R.id.textImportMessage).visibility = View.GONE
        findViewById<View>(R.id.layoutImportPreview).visibility = View.VISIBLE
        findViewById<View>(R.id.layoutImportBar).visibility = View.VISIBLE

        val group = findViewById<ChipGroup>(R.id.chipGroupImportPet)
        group.setOnCheckedStateChangeListener(null)
        group.removeAllViews()
        if (pets.none { it.id == selectedPetId }) selectedPetId = pets.first().id
        pets.forEach { pet ->
            val chip = Chip(this).apply {
                id = View.generateViewId()
                tag = pet.id
                text = pet.name
                isCheckable = true
                isCheckedIconVisible = false
                chipStrokeWidth = resources.displayMetrics.density
                setTextColor(ContextCompat.getColorStateList(this@ImportRecordsActivity, R.color.chip_selectable_text))
                chipBackgroundColor = ContextCompat.getColorStateList(this@ImportRecordsActivity, R.color.chip_selectable_bg)
                chipStrokeColor = ContextCompat.getColorStateList(this@ImportRecordsActivity, R.color.chip_selectable_stroke)
            }
            group.addView(chip)
            if (pet.id == selectedPetId) group.check(chip.id)
        }
        group.setOnCheckedStateChangeListener { g, ids ->
            selectedPetId = ids.firstOrNull()?.let { g.findViewById<Chip>(it)?.tag as? Long } ?: return@setOnCheckedStateChangeListener
            showRecords()
        }
        showRecords()
    }

    /** Lists parsed records; ones the pet already has are shown but can't be imported twice. */
    private fun showRecords() {
        val petId = selectedPetId
        runInBackground({ database.getHealthcareHistory(petId).map { key(it.type, it.date, it.notes) }.toSet() }) { existing ->
            if (petId != selectedPetId) return@runInBackground
            val container = findViewById<LinearLayout>(R.id.layoutImportRows)
            container.removeAllViews()
            val inflater = LayoutInflater.from(this)
            candidates = parsed.mapIndexed { index, record ->
                if (index > 0) container.addView(View(this).apply {
                    setBackgroundColor(ContextCompat.getColor(this@ImportRecordsActivity, R.color.md_outline_variant))
                    layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1).apply { marginStart = 16.dp() }
                })
                val duplicate = key(record.type, record.date, record.notes) in existing
                val row = inflater.inflate(R.layout.item_history_row, container, false)
                val style = healthRecordStyle(record.type)
                row.findViewById<View>(R.id.historyIconBg).backgroundTintList = ContextCompat.getColorStateList(this, style.bg)
                row.findViewById<android.widget.ImageView>(R.id.historyIcon).apply {
                    setImageResource(style.icon)
                    imageTintList = ContextCompat.getColorStateList(this@ImportRecordsActivity, style.fg)
                }
                row.findViewById<TextView>(R.id.historyTitle).text = record.type
                row.findViewById<TextView>(R.id.historySubtitle).text =
                    listOf(displayDate(record.date), record.notes).filter { it.isNotBlank() }.joinToString(" · ")
                row.findViewById<TextView>(R.id.historyTrailing).apply {
                    text = if (duplicate) "Already added" else ""
                    setTextColor(ContextCompat.getColor(this@ImportRecordsActivity, R.color.app_text_secondary))
                    visibility = if (duplicate) View.VISIBLE else View.GONE
                }
                val checkBox = MaterialCheckBox(this).apply {
                    isChecked = !duplicate
                    isEnabled = !duplicate
                    contentDescription = "Import ${record.type} on ${displayDate(record.date)}"
                    setOnCheckedChangeListener { _, _ -> updateImportButton() }
                }
                (row as LinearLayout).addView(checkBox, 0)
                row.setOnClickListener { if (checkBox.isEnabled) checkBox.toggle() }
                row.alpha = if (duplicate) 0.6f else 1f
                container.addView(row)
                Candidate(record, checkBox)
            }
            updateImportButton()
        }
    }

    private fun updateImportButton() {
        val count = candidates.count { it.checkBox.isChecked }
        findViewById<MaterialButton>(R.id.buttonImport).apply {
            isEnabled = count > 0
            text = if (count == 0) "Nothing selected" else "Import $count record${if (count == 1) "" else "s"}"
        }
    }

    // endregion

    private fun importSelected() {
        val petId = selectedPetId
        val selected = candidates.filter { it.checkBox.isChecked }.map { it.record }
        if (selected.isEmpty()) return
        findViewById<View>(R.id.buttonImport).isEnabled = false
        runInBackground({
            selected.count { record ->
                val recordId = database.addHealthcareRecord(petId, record.type, record.date, record.notes)
                if (recordId != -1L) HealthSchedule.onRecordAdded(this, database, petId, recordId, record.type, record.date)
                recordId != -1L
            }
        }) { saved ->
            Toast.makeText(this, "Imported $saved record${if (saved == 1) "" else "s"}", Toast.LENGTH_SHORT).show()
            // Show the result on the pet's profile.
            startActivity(Intent(this, PetDetailsActivity::class.java).putExtra("EXTRA_PET_ID", petId))
            finish()
        }
    }

    private fun key(type: String, date: String, notes: String) =
        "${type.lowercase(Locale.US)}|${parseExpenseDate(date)?.time ?: date}|${notes.trim().lowercase(Locale.US)}"

    private fun <T> runInBackground(work: () -> T, onResult: (T) -> Unit) {
        if (executor.isShutdown) return
        executor.execute {
            val result = work()
            runOnUiThread { if (!isFinishing && !isDestroyed) onResult(result) }
        }
    }

    private fun Int.dp(): Int = (this * resources.displayMetrics.density).roundToInt()

    companion object {
        private const val MAX_FILE_BYTES = 1_000_000
    }
}
