package com.example.petcare

import android.app.AlarmManager
import android.app.DatePickerDialog
import android.app.Dialog
import android.app.PendingIntent
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.imageview.ShapeableImageView
import com.google.android.material.shape.CornerFamily
import com.google.android.material.shape.ShapeAppearanceModel
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

class PetDetailsActivity : AppCompatActivity() {

    /** Everything shown on the page, loaded together off the main thread. */
    private data class PetProfile(
        val name: String,
        val species: String,
        val breed: String,
        val age: Int,
        val weight: Double,
        val diet: String,
        val vaccineDate: String,
        val allergies: String,
        val toys: String,
        val notes: String,
        val photos: List<String>,
        val records: List<HealthcareRecord>,
        val tasksDone: Int,
        val tasksTotal: Int,
        val totalSpent: Double
    )

    private lateinit var database: AuthDatabaseHelper
    private val executor: ExecutorService = Executors.newSingleThreadExecutor()
    private var petId: Long = -1L
    private var petName: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_pet_details)

        database = AuthDatabaseHelper(this)
        petId = intent.getLongExtra("EXTRA_PET_ID", -1L)
        if (petId == -1L) {
            finish()
            return
        }

        updateStatusBarIcons()
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(android.R.id.content)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        findViewById<MaterialToolbar>(R.id.toolbar).apply {
            setNavigationOnClickListener { finish() }
            setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    R.id.actionEditPet -> openEditor()
                    R.id.actionDeletePet -> showDeletePetConfirmation()
                    else -> return@setOnMenuItemClickListener false
                }
                true
            }
        }

        findViewById<View>(R.id.buttonAddHistory).setOnClickListener { showAddHistoryDialog() }
        findViewById<View>(R.id.buttonManagePhotos).setOnClickListener { openEditor() }
        findViewById<View>(R.id.buttonPetChecklist).setOnClickListener {
            startActivity(
                Intent(this, ChecklistActivity::class.java)
                    .putExtra(ChecklistActivity.EXTRA_PET_ID, petId)
                    .putExtra(ChecklistActivity.EXTRA_PET_NAME, petName)
            )
        }
        findViewById<View>(R.id.buttonPetAddTask).setOnClickListener {
            startActivity(Intent(this, AddTaskActivity::class.java).putExtra(AddTaskActivity.EXTRA_SELECTED_PET_ID, petId))
        }
        findViewById<View>(R.id.buttonPetAddExpense).setOnClickListener {
            startActivity(Intent(this, AddExpenseActivity::class.java).putExtra(AddExpenseActivity.EXTRA_SELECTED_PET_ID, petId))
        }
    }

    override fun onResume() {
        super.onResume()
        // Reload every time so edits, new tasks and expenses show up on return.
        if (petId != -1L) loadProfile()
    }

    override fun onDestroy() {
        executor.shutdownNow()
        super.onDestroy()
    }

    // region Loading

    private fun loadProfile() {
        runInBackground({ readProfile() }) { profile ->
            if (profile == null) {
                // Pet was deleted or isn't accessible.
                finish()
                return@runInBackground
            }
            render(profile)
        }
    }

    private fun readProfile(): PetProfile? {
        val values = database.getPetById(petId) ?: return null
        val tasks = database.getCareTasks(petId)
        return PetProfile(
            name = values.getAsString("name").orEmpty(),
            species = values.getAsString("species").orEmpty(),
            breed = values.getAsString("breed").orEmpty(),
            age = values.getAsInteger("age") ?: 0,
            weight = values.getAsDouble("weight") ?: 0.0,
            diet = values.getAsString("diet").orEmpty(),
            vaccineDate = values.getAsString("vaccine_date").orEmpty(),
            allergies = values.getAsString("allergies").orEmpty(),
            toys = values.getAsString("toys").orEmpty(),
            notes = values.getAsString("notes").orEmpty(),
            photos = database.getPetPhotos(petId),
            // Stored as text (d/M/yyyy), so sort by the parsed date, newest first.
            records = database.getHealthcareHistory(petId)
                .sortedByDescending { parseExpenseDate(it.date)?.time ?: Long.MIN_VALUE },
            tasksDone = tasks.count { it.isCompleted },
            tasksTotal = tasks.size,
            totalSpent = database.getExpenses(petId).sumOf { it.amount }
        )
    }

    // endregion

    // region Rendering

    private fun render(p: PetProfile) {
        petName = p.name
        findViewById<TextView>(R.id.textPetNameHeader).text = p.name.ifBlank { "Unnamed pet" }
        findViewById<TextView>(R.id.textPetBreedHeader).text =
            listOf(p.species, p.breed).filter { it.isNotBlank() && it != "Unknown" }.joinToString(" · ").ifBlank { "Pet" }

        findViewById<TextView>(R.id.textPetTodayBadge).apply {
            visibility = if (p.tasksTotal > 0) View.VISIBLE else View.GONE
            text = if (p.tasksDone == p.tasksTotal) "All ${p.tasksTotal} routines done today"
            else "${p.tasksDone} of ${p.tasksTotal} routines done today"
        }

        findViewById<TextView>(R.id.textPetAgeVal).text = when {
            p.age <= 0 -> "-"
            p.age == 1 -> "1 yr"
            else -> "${p.age} yrs"
        }
        findViewById<TextView>(R.id.textPetWeightVal).text =
            if (p.weight > 0) "${formatNumber(p.weight)} kg" else "-"
        findViewById<TextView>(R.id.textPetSpentVal).text = formatMoney(p.totalSpent)

        renderAvatar(p.photos.firstOrNull())
        renderVaccine(p.vaccineDate)
        renderAbout(p)
        renderPhotos(p.photos)
        renderRecords(p.records)

        findViewById<View>(R.id.layoutPetContent).visibility = View.VISIBLE
    }

    private fun renderAvatar(uri: String?) {
        val avatar = findViewById<ShapeableImageView>(R.id.imagePetAvatar)
        val placeholder = findViewById<View>(R.id.imagePetAvatarPlaceholder)
        if (uri == null) {
            avatar.setImageDrawable(null)
            placeholder.visibility = View.VISIBLE
            avatar.setOnClickListener(null)
            return
        }
        placeholder.visibility = View.GONE
        PetImageLoader.load(avatar, uri, 112.dp() * 2) { placeholder.visibility = View.VISIBLE }
        avatar.setOnClickListener { showPhoto(uri) }
    }

    private fun renderVaccine(stored: String) {
        val dateText = findViewById<TextView>(R.id.textPetVaccine)
        val status = findViewById<TextView>(R.id.textVaccineStatus)
        val date = parseExpenseDate(stored)
        if (date == null) {
            dateText.text = if (stored.isBlank()) "Not scheduled" else stored
            status.visibility = View.GONE
            return
        }
        dateText.text = displayDate(stored)

        val today = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        val days = TimeUnit.MILLISECONDS.toDays(date.time - today.timeInMillis).toInt()
        val (label, bg, fg) = when {
            days < 0 -> Triple("Overdue ${-days}d", R.color.md_error_container, R.color.md_on_error_container)
            days == 0 -> Triple("Today", R.color.md_warning_container, R.color.md_on_warning_container)
            days <= 14 -> Triple("In $days day${if (days == 1) "" else "s"}", R.color.md_warning_container, R.color.md_on_warning_container)
            else -> Triple("In $days days", R.color.md_surface_container_high, R.color.app_text_primary)
        }
        status.visibility = View.VISIBLE
        status.text = label
        status.backgroundTintList = ContextCompat.getColorStateList(this, bg)
        status.setTextColor(color(fg))
    }

    private fun renderAbout(p: PetProfile) {
        val container = findViewById<LinearLayout>(R.id.layoutPetAbout)
        container.removeAllViews()
        val rows = listOf(
            AboutRow("Diet", p.diet, R.drawable.ic_bowl),
            AboutRow(
                "Allergies & medical warnings", p.allergies, R.drawable.ic_status_alert,
                warn = p.allergies.isNotBlank() && p.allergies.lowercase() !in listOf("none", "no known allergies")
            ),
            AboutRow("Favourite toys & comfort items", p.toys, R.drawable.ic_paw),
            AboutRow("Behaviour notes", p.notes, R.drawable.ic_checklist)
        )
        val inflater = LayoutInflater.from(this)
        rows.forEachIndexed { index, row ->
            if (index > 0) container.addView(divider(0))
            val view = inflater.inflate(R.layout.item_pet_about_row, container, false)
            view.findViewById<ImageView>(R.id.aboutIcon).apply {
                setImageResource(row.icon)
                imageTintList = ContextCompat.getColorStateList(
                    this@PetDetailsActivity, if (row.warn) R.color.app_accent_red else R.color.app_text_secondary
                )
            }
            view.findViewById<TextView>(R.id.aboutLabel).text = row.label
            view.findViewById<TextView>(R.id.aboutValue).apply {
                text = row.value.ifBlank { "Not added" }
                setTextColor(color(
                    when {
                        row.value.isBlank() -> R.color.app_text_secondary
                        row.warn -> R.color.app_accent_red
                        else -> R.color.app_text_primary
                    }
                ))
            }
            container.addView(view)
        }
    }

    private data class AboutRow(val label: String, val value: String, val icon: Int, val warn: Boolean = false)

    private fun renderPhotos(photos: List<String>) {
        val layout = findViewById<LinearLayout>(R.id.layoutDetailsPhotos)
        layout.removeAllViews()
        findViewById<TextView>(R.id.textPhotosHeader).text = if (photos.isEmpty()) "Photos" else "Photos (${photos.size})"
        findViewById<View>(R.id.scrollPhotos).visibility = if (photos.isEmpty()) View.GONE else View.VISIBLE
        findViewById<View>(R.id.textNoPhotos).visibility = if (photos.isEmpty()) View.VISIBLE else View.GONE

        val size = 104.dp()
        photos.forEach { uri ->
            val image = ShapeableImageView(this).apply {
                layoutParams = LinearLayout.LayoutParams(size, size).apply { marginEnd = 10.dp() }
                scaleType = ImageView.ScaleType.CENTER_CROP
                setBackgroundColor(color(R.color.md_surface_container_high))
                shapeAppearanceModel = ShapeAppearanceModel.builder()
                    .setAllCorners(CornerFamily.ROUNDED, 16.dp().toFloat())
                    .build()
                contentDescription = "Photo of $petName"
                setOnClickListener { showPhoto(uri) }
            }
            PetImageLoader.load(image, uri, size * 2)
            layout.addView(image)
        }
    }

    private fun renderRecords(records: List<HealthcareRecord>) {
        val layout = findViewById<LinearLayout>(R.id.layoutHealthcareHistory)
        layout.removeAllViews()
        findViewById<View>(R.id.textNoHistory).visibility = if (records.isEmpty()) View.VISIBLE else View.GONE
        layout.visibility = if (records.isEmpty()) View.GONE else View.VISIBLE

        val inflater = LayoutInflater.from(this)
        records.forEachIndexed { index, record ->
            if (index > 0) layout.addView(divider(66.dp()))
            val row = inflater.inflate(R.layout.item_health_record, layout, false)
            val style = recordStyle(record.type)
            row.findViewById<View>(R.id.recordIconBg).backgroundTintList = ContextCompat.getColorStateList(this, style.bg)
            row.findViewById<ImageView>(R.id.recordIcon).apply {
                setImageResource(style.icon)
                imageTintList = ContextCompat.getColorStateList(this@PetDetailsActivity, style.fg)
            }
            row.findViewById<TextView>(R.id.recordTitle).text = record.type.ifBlank { "Record" }
            row.findViewById<TextView>(R.id.recordMeta).text =
                listOf(displayDate(record.date), record.notes).filter { it.isNotBlank() }.joinToString(" · ")
            row.findViewById<ImageButton>(R.id.recordDelete).setOnClickListener { showDeleteHistoryConfirmation(record) }
            layout.addView(row)
        }
    }

    private data class RecordStyle(val icon: Int, val bg: Int, val fg: Int)

    private fun recordStyle(type: String): RecordStyle {
        val t = type.lowercase()
        return when {
            "vacc" in t -> RecordStyle(R.drawable.ic_meds, R.color.cat_healthcare_bg, R.color.cat_healthcare_fg)
            "check" in t || "exam" in t -> RecordStyle(R.drawable.ic_status_check, R.color.cat_exercise_bg, R.color.cat_exercise_fg)
            "surg" in t -> RecordStyle(R.drawable.ic_status_alert, R.color.cat_medication_bg, R.color.cat_medication_fg)
            "med" in t -> RecordStyle(R.drawable.ic_meds, R.color.cat_grooming_bg, R.color.cat_grooming_fg)
            else -> RecordStyle(R.drawable.ic_paw, R.color.cat_cleaning_bg, R.color.cat_cleaning_fg)
        }
    }

    // endregion

    // region Actions

    private fun openEditor() {
        startActivity(Intent(this, AddEditPetActivity::class.java).putExtra("EXTRA_PET_ID", petId))
    }

    private fun showPhoto(uri: String) {
        val dialog = Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
        val image = ImageView(this).apply {
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            scaleType = ImageView.ScaleType.FIT_CENTER
            contentDescription = "Photo of $petName. Tap to close."
            setOnClickListener { dialog.dismiss() }
        }
        dialog.setContentView(image)
        PetImageLoader.load(image, uri, resources.displayMetrics.widthPixels.coerceAtLeast(resources.displayMetrics.heightPixels))
        dialog.show()
    }

    private fun showDeletePetConfirmation() {
        MaterialAlertDialogBuilder(this)
            .setTitle("Delete ${petName.ifBlank { "this pet" }}?")
            .setMessage("This removes the pet along with its routines, expenses, photos and health records. This can't be undone.")
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Delete") { _, _ ->
                runInBackground({ database.deletePet(petId) }) { deleted ->
                    if (deleted) {
                        cancelVaccineReminder()
                        Toast.makeText(this, "${petName.ifBlank { "Pet" }} deleted", Toast.LENGTH_SHORT).show()
                        finish()
                    } else {
                        showMessage("Couldn't delete this pet. Please try again.")
                    }
                }
            }
            .show()
    }

    /** Matches the alarm scheduled in [AddEditPetActivity] (request code petId + 10000). */
    private fun cancelVaccineReminder() {
        val pending = PendingIntent.getBroadcast(
            this, petId.toInt() + 10000, Intent(this, ReminderReceiver::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_NO_CREATE
        ) ?: return
        getSystemService(AlarmManager::class.java)?.cancel(pending)
        pending.cancel()
    }

    private fun showDeleteHistoryConfirmation(record: HealthcareRecord) {
        MaterialAlertDialogBuilder(this)
            .setTitle("Delete record?")
            .setMessage("Do you want to delete the ${record.type.ifBlank { "health" }} record from ${displayDate(record.date)}?")
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Delete") { _, _ ->
                runInBackground({ database.deleteHealthcareRecord(record.id) }) { deleted ->
                    showMessage(if (deleted) "Record deleted" else "Couldn't delete record.")
                    if (deleted) loadProfile()
                }
            }
            .show()
    }

    private fun showAddHistoryDialog() {
        val form = LayoutInflater.from(this).inflate(R.layout.dialog_health_record, null)
        val typeGroup = form.findViewById<ChipGroup>(R.id.chipGroupRecordType)
        val dateLayout = form.findViewById<TextInputLayout>(R.id.layoutRecordDate)
        val dateInput = form.findViewById<TextInputEditText>(R.id.inputRecordDate)
        val notesInput = form.findViewById<TextInputEditText>(R.id.inputRecordNotes)

        RECORD_TYPES.forEachIndexed { index, type ->
            typeGroup.addView(Chip(this).apply {
                id = View.generateViewId()
                text = type
                isCheckable = true
                isCheckedIconVisible = false
                setTextColor(ContextCompat.getColorStateList(this@PetDetailsActivity, R.color.chip_selectable_text))
                chipBackgroundColor = ContextCompat.getColorStateList(this@PetDetailsActivity, R.color.chip_selectable_bg)
                chipStrokeColor = ContextCompat.getColorStateList(this@PetDetailsActivity, R.color.chip_selectable_stroke)
                chipStrokeWidth = resources.displayMetrics.density
                if (index == 0) isChecked = true
            })
        }

        val picked = Calendar.getInstance()
        fun showDate() = dateInput.setText(SimpleDateFormat("d MMM yyyy", Locale.getDefault()).format(picked.time))
        showDate()
        val openPicker = View.OnClickListener {
            DatePickerDialog(this, { _, y, m, d ->
                picked.set(y, m, d)
                showDate()
                dateLayout.error = null
            }, picked.get(Calendar.YEAR), picked.get(Calendar.MONTH), picked.get(Calendar.DAY_OF_MONTH)).show()
        }
        dateInput.setOnClickListener(openPicker)
        dateLayout.setEndIconOnClickListener(openPicker)

        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle("Add health record")
            .setView(form)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Save", null)
            .show()

        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            if (picked.after(Calendar.getInstance())) {
                dateLayout.error = "Date can't be in the future"
                return@setOnClickListener
            }
            val type = typeGroup.findViewById<Chip>(typeGroup.checkedChipId)?.text?.toString() ?: RECORD_TYPES.first()
            val date = SimpleDateFormat("dd/MM/yyyy", Locale.US).format(picked.time)
            val notes = notesInput.text?.toString()?.trim().orEmpty()
            dialog.dismiss()
            runInBackground({ database.saveHealthcareRecord(petId, type, date, notes) }) { saved ->
                showMessage(if (saved) "$type record added" else "Couldn't save record.")
                if (saved) loadProfile()
            }
        }
    }

    // endregion

    // region Helpers

    private fun <T> runInBackground(work: () -> T, onResult: (T) -> Unit) {
        if (executor.isShutdown) return
        executor.execute {
            val result = work()
            runOnUiThread { if (!isFinishing && !isDestroyed) onResult(result) }
        }
    }

    private fun showMessage(message: String) {
        Snackbar.make(findViewById(R.id.main_pet_details), message, Snackbar.LENGTH_SHORT).show()
    }

    private fun divider(startMargin: Int): View = View(this).apply {
        setBackgroundColor(color(R.color.md_outline_variant))
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1).apply { marginStart = startMargin }
    }

    private fun formatNumber(value: Double): String =
        if (value % 1.0 == 0.0) value.toInt().toString() else String.format(Locale.getDefault(), "%.1f", value)

    private fun color(res: Int): Int = ContextCompat.getColor(this, res)

    private fun updateStatusBarIcons() {
        val isDarkMode = (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES
        WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightStatusBars = !isDarkMode
    }

    private fun Int.dp(): Int = (this * resources.displayMetrics.density).roundToInt()

    // endregion

    companion object {
        private val RECORD_TYPES = listOf("Vaccination", "Check-up", "Medication", "Surgery", "Other")
    }
}
