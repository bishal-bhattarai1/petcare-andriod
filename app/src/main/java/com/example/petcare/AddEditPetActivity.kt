package com.example.petcare

import android.app.DatePickerDialog
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.imageview.ShapeableImageView
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.google.android.material.shape.CornerFamily
import com.google.android.material.shape.ShapeAppearanceModel
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.textfield.TextInputLayout
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlin.math.roundToInt

class AddEditPetActivity : AppCompatActivity() {
    private lateinit var database: AuthDatabaseHelper

    private lateinit var inputPetName: EditText
    private lateinit var inputBreed: EditText
    private lateinit var inputAge: EditText
    private lateinit var inputWeight: EditText
    private lateinit var inputDiet: EditText
    private lateinit var inputVaccineDate: EditText
    private lateinit var inputAllergies: EditText
    private lateinit var inputToys: EditText
    private lateinit var inputNotes: EditText
    private lateinit var inputOtherSpecies: EditText

    private lateinit var chipGroupSpecies: ChipGroup
    private lateinit var progressCompletion: LinearProgressIndicator
    private lateinit var textCompletion: TextView
    private lateinit var switchReminder: MaterialSwitch
    private lateinit var layoutPhotos: LinearLayout
    private lateinit var imagePetMain: ShapeableImageView

    private var editingPetId: Long = -1L
    private val selectedPhotos = mutableListOf<String>()

    /** Set once the user changes anything, so leaving asks before discarding it. */
    private var hasUnsavedChanges = false
    private var isLoading = false
    private lateinit var chipGroupAllergies: ChipGroup

    private val pickMultipleMedia = registerForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(MAX_PHOTOS)) { uris ->
        if (uris.isEmpty()) return@registerForActivityResult
        val room = MAX_PHOTOS - selectedPhotos.size
        uris.take(room).forEach { uri ->
            try {
                contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (_: SecurityException) {
                // Some providers don't offer persistable access; the photo still works this session.
            }
            if (uri.toString() !in selectedPhotos) selectedPhotos.add(uri.toString())
        }
        if (uris.size > room) {
            Toast.makeText(this, "You can add up to $MAX_PHOTOS photos", Toast.LENGTH_SHORT).show()
        }
        markChanged()
        renderPhotos()
        updateProgress()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_add_edit_pet)
        database = AuthDatabaseHelper(this)

        updateStatusBarIcons()

        findViewById<Toolbar>(R.id.toolbar).setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = confirmDiscardOrFinish()
        })

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(android.R.id.content)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        initViews()
        setupListeners()

        editingPetId = intent.getLongExtra("EXTRA_PET_ID", -1L)
        if (editingPetId != -1L) loadExistingPet(editingPetId)
        renderPhotos()
        updateProgress()
    }

    private fun loadExistingPet(id: Long) {
        val data = database.getPetById(id) ?: return
        isLoading = true

        inputPetName.setText(data.getAsString("name"))
        inputBreed.setText(data.getAsString("breed"))
        inputAge.setText(data.getAsInteger("age")?.takeIf { it > 0 }?.toString().orEmpty())
        inputWeight.setText(data.getAsDouble("weight")?.takeIf { it > 0 }?.let { formatNumber(it) }.orEmpty())
        inputDiet.setText(data.getAsString("diet"))
        inputVaccineDate.setText(data.getAsString("vaccine_date"))
        setAllergies(data.getAsString("allergies").orEmpty())
        inputToys.setText(data.getAsString("toys"))
        inputNotes.setText(data.getAsString("notes"))

        when (val species = data.getAsString("species")) {
            "Dog" -> chipGroupSpecies.check(R.id.chipDog)
            "Cat" -> chipGroupSpecies.check(R.id.chipCat)
            "Bird" -> chipGroupSpecies.check(R.id.chipBird)
            "Rabbit" -> chipGroupSpecies.check(R.id.chipRabbit)
            null, "", "Unknown" -> Unit
            else -> {
                chipGroupSpecies.check(R.id.chipOther)
                inputOtherSpecies.setText(species)
                findViewById<View>(R.id.layoutOtherSpecies).visibility = View.VISIBLE
            }
        }

        switchReminder.isChecked = data.getAsInteger("reminder_enabled") == 1

        selectedPhotos.clear()
        selectedPhotos.addAll(database.getPetPhotos(id))

        findViewById<TextView>(R.id.textPetFormTitle).text = "Edit pet"
        findViewById<Button>(R.id.buttonSavePet).text = "Save changes"
        isLoading = false
    }

    private fun initViews() {
        inputPetName = findViewById(R.id.inputPetName)
        inputBreed = findViewById(R.id.inputBreed)
        inputAge = findViewById(R.id.inputAge)
        inputWeight = findViewById(R.id.inputWeight)
        inputDiet = findViewById(R.id.inputDiet)
        inputVaccineDate = findViewById(R.id.inputVaccineDate)
        inputAllergies = findViewById(R.id.inputAllergies)
        chipGroupAllergies = findViewById(R.id.chipGroupAllergies)
        inputToys = findViewById(R.id.inputToys)
        inputNotes = findViewById(R.id.inputNotes)
        inputOtherSpecies = findViewById(R.id.inputOtherSpecies)

        chipGroupSpecies = findViewById(R.id.chipGroupSpecies)
        progressCompletion = findViewById(R.id.progressCompletion)
        textCompletion = findViewById(R.id.textCompletion)
        switchReminder = findViewById(R.id.switchReminder)
        layoutPhotos = findViewById(R.id.layoutPetPhotos)
        imagePetMain = findViewById(R.id.imagePetMain)
    }

    // region Photos

    /** Main avatar = first photo; thumbnails in order, tap one for options. */
    private fun renderPhotos() {
        val main = selectedPhotos.firstOrNull()
        if (main != null) {
            imagePetMain.setPadding(0, 0, 0, 0)
            imagePetMain.scaleType = ImageView.ScaleType.CENTER_CROP
            imagePetMain.imageTintList = null
            PetImageLoader.load(imagePetMain, main, 104.dp() * 2)
        } else {
            imagePetMain.tag = null
            imagePetMain.setPadding(26.dp(), 26.dp(), 26.dp(), 26.dp())
            imagePetMain.scaleType = ImageView.ScaleType.CENTER_INSIDE
            imagePetMain.setImageResource(R.drawable.ic_paw)
            imagePetMain.imageTintList = ContextCompat.getColorStateList(this, R.color.md_on_secondary_container)
        }

        val addButton = findViewById<View>(R.id.buttonAddMorePhotos)
        layoutPhotos.removeAllViews()
        val size = 64.dp()
        selectedPhotos.forEachIndexed { index, uri ->
            val thumb = ShapeableImageView(this).apply {
                layoutParams = LinearLayout.LayoutParams(size, size).apply { marginEnd = 8.dp() }
                scaleType = ImageView.ScaleType.CENTER_CROP
                setBackgroundColor(ContextCompat.getColor(this@AddEditPetActivity, R.color.md_surface_container_high))
                shapeAppearanceModel = ShapeAppearanceModel.builder()
                    .setAllCorners(CornerFamily.ROUNDED, 12.dp().toFloat())
                    .build()
                if (index == 0) {
                    strokeColor = ContextCompat.getColorStateList(this@AddEditPetActivity, R.color.md_secondary)
                    strokeWidth = 2.dp().toFloat()
                    setPadding(1.dp(), 1.dp(), 1.dp(), 1.dp())
                }
                contentDescription = if (index == 0) "Main photo. Tap for options." else "Photo ${index + 1}. Tap for options."
                setOnClickListener { showPhotoOptions(uri) }
            }
            PetImageLoader.load(thumb, uri, size * 2)
            layoutPhotos.addView(thumb)
        }
        addButton.visibility = if (selectedPhotos.size < MAX_PHOTOS) View.VISIBLE else View.GONE
        layoutPhotos.addView(addButton)
    }

    private fun showPhotoOptions(uri: String) {
        val isMain = selectedPhotos.firstOrNull() == uri
        val options = if (isMain) arrayOf("Remove photo") else arrayOf("Make main photo", "Remove photo")
        MaterialAlertDialogBuilder(this)
            .setItems(options) { _, which ->
                when (options[which]) {
                    "Make main photo" -> {
                        selectedPhotos.remove(uri)
                        selectedPhotos.add(0, uri)
                    }
                    "Remove photo" -> selectedPhotos.remove(uri)
                }
                markChanged()
                renderPhotos()
                updateProgress()
            }
            .show()
    }

    private fun pickPhotos() {
        if (selectedPhotos.size >= MAX_PHOTOS) {
            Toast.makeText(this, "You can add up to $MAX_PHOTOS photos", Toast.LENGTH_SHORT).show()
            return
        }
        pickMultipleMedia.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }

    // endregion

    private fun setupListeners() {
        findViewById<View>(R.id.buttonAddMorePhotos).setOnClickListener { pickPhotos() }
        findViewById<View>(R.id.buttonCamera).setOnClickListener { pickPhotos() }
        imagePetMain.setOnClickListener { pickPhotos() }

        chipGroupSpecies.setOnCheckedStateChangeListener { _, checkedIds ->
            val isOther = checkedIds.contains(R.id.chipOther)
            findViewById<View>(R.id.layoutOtherSpecies).visibility = if (isOther) View.VISIBLE else View.GONE
            markChanged()
            updateProgress()
        }

        val openDatePicker = View.OnClickListener { showVaccineDatePicker() }
        inputVaccineDate.setOnClickListener(openDatePicker)
        findViewById<TextInputLayout>(R.id.layoutVaccineDate).setEndIconOnClickListener(openDatePicker)
        switchReminder.setOnCheckedChangeListener { _, _ -> markChanged() }

        val watcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                markChanged()
                updateProgress()
            }
        }
        listOf(inputPetName, inputBreed, inputAge, inputWeight, inputDiet, inputVaccineDate, inputAllergies, inputToys, inputNotes, inputOtherSpecies)
            .forEach { it.addTextChangedListener(watcher) }

        // Clear a field's error as soon as the user edits it.
        listOf(R.id.layoutPetName to inputPetName, R.id.layoutAge to inputAge, R.id.layoutWeight to inputWeight)
            .forEach { (layoutId, input) ->
                input.addTextChangedListener(object : TextWatcher {
                    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                    override fun afterTextChanged(s: Editable?) {
                        findViewById<TextInputLayout>(layoutId).error = null
                    }
                })
            }

        setupAllergyChips()
        inputAllergies.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                if (!s.isNullOrBlank()) noneChip().isChecked = false
            }
        })

        findViewById<Button>(R.id.buttonSavePet).setOnClickListener { save() }
    }

    /** The next due date can't be in the past, so a past "last vaccinated" date can't be picked by mistake. */
    private fun showVaccineDatePicker() {
        val today = startOfToday()
        val calendar = Calendar.getInstance()
        parseExpenseDate(inputVaccineDate.text.toString())?.takeIf { !it.before(today.time) }?.let { calendar.time = it }
        DatePickerDialog(this, { _, year, month, day ->
            calendar.set(year, month, day)
            inputVaccineDate.setText(SimpleDateFormat("dd/MM/yyyy", Locale.US).format(calendar.time))
            findViewById<TextInputLayout>(R.id.layoutVaccineDate).error = null
        }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH)).apply {
            datePicker.minDate = today.timeInMillis
            if (inputVaccineDate.text.isNotBlank()) {
                setButton(DatePickerDialog.BUTTON_NEUTRAL, "Clear") { _, _ -> inputVaccineDate.setText("") }
            }
        }.show()
    }

    private fun save() {
        val name = inputPetName.text.toString().trim()
        val ageText = inputAge.text.toString().trim()
        val weightText = inputWeight.text.toString().trim()
        val age = ageText.toIntOrNull()
        val weight = weightText.toDoubleOrNull()

        var valid = true
        if (name.isEmpty()) {
            findViewById<TextInputLayout>(R.id.layoutPetName).error = "Name is required"
            valid = false
        }
        if (ageText.isNotEmpty() && (age == null || age !in 0..MAX_AGE)) {
            findViewById<TextInputLayout>(R.id.layoutAge).error = "0-$MAX_AGE"
            valid = false
        }
        if (weightText.isNotEmpty() && (weight == null || weight <= 0 || weight > MAX_WEIGHT_KG)) {
            findViewById<TextInputLayout>(R.id.layoutWeight).error = "Enter a weight between 0 and $MAX_WEIGHT_KG kg"
            valid = false
        }
        val vaccineDue = parseExpenseDate(inputVaccineDate.text.toString().trim())
        if (vaccineDue != null && vaccineDue.before(startOfToday().time)) {
            findViewById<TextInputLayout>(R.id.layoutVaccineDate).error = "This date has passed. Pick the next due date or clear it."
            valid = false
        }
        if (!valid) {
            Toast.makeText(this, "Please fix the highlighted fields", Toast.LENGTH_SHORT).show()
            return
        }

        val saveButton = findViewById<Button>(R.id.buttonSavePet)
        saveButton.isEnabled = false // Prevents a double tap from creating two pets.

        val species = getSelectedSpecies()
        val vaccineDate = inputVaccineDate.text.toString().trim()
        val petId = if (editingPetId != -1L) {
            val success = database.updatePet(
                editingPetId, name, species, inputBreed.text.toString().trim(), age ?: 0, weight ?: 0.0,
                inputDiet.text.toString().trim(), vaccineDate, switchReminder.isChecked,
                allergiesValue(), inputToys.text.toString().trim(), inputNotes.text.toString().trim()
            )
            if (success) editingPetId else -1L
        } else {
            database.savePet(
                name, species, inputBreed.text.toString().trim(), age ?: 0, weight ?: 0.0,
                inputDiet.text.toString().trim(), vaccineDate, switchReminder.isChecked,
                allergiesValue(), inputToys.text.toString().trim(), inputNotes.text.toString().trim()
            )
        }

        if (petId == -1L) {
            saveButton.isEnabled = true
            Toast.makeText(this, "Couldn't save the pet. Please try again.", Toast.LENGTH_SHORT).show()
            return
        }

        database.savePetPhotos(petId, selectedPhotos)
        if (switchReminder.isChecked && vaccineDate.isNotBlank()) {
            VaccineReminder.schedule(this, petId, name, vaccineDate)
        } else {
            VaccineReminder.cancel(this, petId)
        }

        hasUnsavedChanges = false
        Toast.makeText(this, if (editingPetId != -1L) "Changes saved" else "$name added", Toast.LENGTH_SHORT).show()
        finish()
    }

    private fun confirmDiscardOrFinish() {
        if (!hasUnsavedChanges) {
            finish()
            return
        }
        MaterialAlertDialogBuilder(this)
            .setTitle("Discard changes?")
            .setMessage("You have unsaved changes to this pet profile.")
            .setNegativeButton("Keep editing", null)
            .setPositiveButton("Discard") { _, _ -> finish() }
            .show()
    }


    // region Allergies

    private val allergyChips: List<Chip>
        get() = (0 until chipGroupAllergies.childCount).map { chipGroupAllergies.getChildAt(it) as Chip }

    private fun noneChip(): Chip = allergyChips.first { it.text == NO_ALLERGIES }

    private fun setupAllergyChips() {
        allergyChips.forEach { chip ->
            chip.setOnCheckedChangeListener { _, checked ->
                // "None" and specific allergies can't both be selected.
                if (checked && chip.text == NO_ALLERGIES) {
                    allergyChips.filter { it !== chip }.forEach { it.isChecked = false }
                    inputAllergies.setText("")
                } else if (checked) {
                    noneChip().isChecked = false
                }
                markChanged()
                updateProgress()
            }
        }
    }

    /** Saved as one comma-separated value, e.g. "Chicken, Pollen, Lamb". */
    private fun allergiesValue(): String {
        val picked = allergyChips.filter { it.isChecked }.map { it.text.toString() }
        if (NO_ALLERGIES in picked) return NO_ALLERGIES
        val other = inputAllergies.text.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        return (picked + other).distinctBy { it.lowercase() }.joinToString(", ")
    }

    private fun setAllergies(stored: String) {
        val other = mutableListOf<String>()
        stored.split(",").map { it.trim() }.filter { it.isNotEmpty() }.forEach { part ->
            val name = if (part.equals("No known allergies", ignoreCase = true)) NO_ALLERGIES else part
            allergyChips.firstOrNull { it.text.toString().equals(name, ignoreCase = true) }
                ?.let { it.isChecked = true } ?: other.add(part)
        }
        inputAllergies.setText(other.joinToString(", "))
    }

    // endregion

    private fun markChanged() {
        if (!isLoading) hasUnsavedChanges = true
    }

    private fun getSelectedSpecies(): String {
        return when (chipGroupSpecies.checkedChipId) {
            R.id.chipDog -> "Dog"
            R.id.chipCat -> "Cat"
            R.id.chipBird -> "Bird"
            R.id.chipRabbit -> "Rabbit"
            R.id.chipOther -> inputOtherSpecies.text.toString().trim().ifEmpty { "Other" }
            else -> "Unknown"
        }
    }

    private fun updateProgress() {
        val fields = listOf(
            inputPetName.text, inputBreed.text, inputAge.text, inputWeight.text, inputDiet.text,
            inputVaccineDate.text, allergiesValue(), inputToys.text, inputNotes.text
        )
        var count = fields.count { !it.isNullOrBlank() }
        if (chipGroupSpecies.checkedChipId != View.NO_ID) count++
        if (selectedPhotos.isNotEmpty()) count++

        val percent = (count * 100) / 11
        progressCompletion.setProgress(percent, true)
        textCompletion.text = getString(R.string.add_pet_completion_text, percent)
    }

    private fun updateStatusBarIcons() {
        val isDarkMode = (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES
        WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightStatusBars = !isDarkMode
    }

    private fun startOfToday(): Calendar = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }

    private fun formatNumber(value: Double): String =
        if (value % 1.0 == 0.0) value.toInt().toString() else value.toString()

    private fun Int.dp(): Int = (this * resources.displayMetrics.density).roundToInt()

    companion object {
        const val NO_ALLERGIES = "None"

        private const val MAX_PHOTOS = 5
        private const val MAX_AGE = 40
        private const val MAX_WEIGHT_KG = 200
    }
}
