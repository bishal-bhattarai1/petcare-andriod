package com.example.petcare

import android.app.DatePickerDialog
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.*
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.google.android.material.chip.ChipGroup
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.google.android.material.switchmaterial.SwitchMaterial
import java.util.*

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
    private lateinit var switchReminder: SwitchMaterial
    private lateinit var layoutPhotos: LinearLayout
    
    private var editingPetId: Long = -1L
    private val selectedPhotos = mutableListOf<String>()

    private val pickMultipleMedia = registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.PickMultipleVisualMedia(5)) { uris ->
        if (uris.isNotEmpty()) {
            uris.forEach { uri ->
                contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                selectedPhotos.add(uri.toString())
            }
            renderThumbnails()
            updateProgress()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_add_edit_pet)
        database = AuthDatabaseHelper(this)

        WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightStatusBars = true

        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        toolbar.setNavigationOnClickListener { finish() }

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(android.R.id.content)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        initViews()
        
        editingPetId = intent.getLongExtra("EXTRA_PET_ID", -1L)
        if (editingPetId != -1L) {
            loadExistingPet(editingPetId)
        }
        
        setupListeners()
    }

    private fun loadExistingPet(id: Long) {
        val data = database.getPetById(id) ?: return
        
        inputPetName.setText(data.getAsString("name"))
        inputBreed.setText(data.getAsString("breed"))
        inputAge.setText(data.getAsString("age"))
        inputWeight.setText(data.getAsString("weight"))
        inputDiet.setText(data.getAsString("diet"))
        inputVaccineDate.setText(data.getAsString("vaccine_date"))
        inputAllergies.setText(data.getAsString("allergies"))
        inputToys.setText(data.getAsString("toys"))
        inputNotes.setText(data.getAsString("notes"))
        
        val species = data.getAsString("species")
        when (species) {
            "Dog" -> chipGroupSpecies.check(R.id.chipDog)
            "Cat" -> chipGroupSpecies.check(R.id.chipCat)
            else -> {
                chipGroupSpecies.check(R.id.chipOther)
                inputOtherSpecies.setText(species)
                findViewById<View>(R.id.layoutOtherSpecies).visibility = View.VISIBLE
            }
        }
        
        val reminder = data.getAsInteger("reminder_enabled") == 1
        switchReminder.isChecked = reminder
        
        // Photos
        val photos = database.getPetPhotos(id)
        selectedPhotos.clear()
        selectedPhotos.addAll(photos)
        renderThumbnails()
        
        findViewById<TextView>(R.id.textCompletion).text = "Editing Profile"
        findViewById<Button>(R.id.buttonSavePet).text = "Update Pet"
    }

    private fun initViews() {
        inputPetName = findViewById(R.id.inputPetName)
        inputBreed = findViewById(R.id.inputBreed)
        inputAge = findViewById(R.id.inputAge)
        inputWeight = findViewById(R.id.inputWeight)
        inputDiet = findViewById(R.id.inputDiet)
        inputVaccineDate = findViewById(R.id.inputVaccineDate)
        inputAllergies = findViewById(R.id.inputAllergies)
        inputToys = findViewById(R.id.inputToys)
        inputNotes = findViewById(R.id.inputNotes)
        inputOtherSpecies = findViewById(R.id.inputOtherSpecies)
        
        chipGroupSpecies = findViewById(R.id.chipGroupSpecies)
        progressCompletion = findViewById(R.id.progressCompletion)
        textCompletion = findViewById(R.id.textCompletion)
        switchReminder = findViewById(R.id.switchReminder)
        layoutPhotos = findViewById(R.id.layoutPetPhotos)
    }

    private fun renderThumbnails() {
        // Keep the "Add More" button which is at the end or start.
        // In XML it is at index 0.
        val addMoreButton = findViewById<View>(R.id.buttonAddMorePhotos)
        layoutPhotos.removeAllViews()
        layoutPhotos.addView(addMoreButton)

        selectedPhotos.forEach { uriString ->
            val imageView = com.google.android.material.imageview.ShapeableImageView(this).apply {
                layoutParams = LinearLayout.LayoutParams(52.dp(), 52.dp()).apply { marginEnd = 8.dp() }
                scaleType = ImageView.ScaleType.CENTER_CROP
                shapeAppearanceModel = com.google.android.material.shape.ShapeAppearanceModel.builder()
                    .setAllCorners(com.google.android.material.shape.CornerFamily.ROUNDED, 8.dp().toFloat())
                    .build()
                setImageURI(android.net.Uri.parse(uriString))
                setOnClickListener {
                    selectedPhotos.remove(uriString)
                    renderThumbnails()
                    updateProgress()
                }
            }
            layoutPhotos.addView(imageView, 0) // Add before the plus button
        }
    }

    private fun Int.dp(): Int = (this * resources.displayMetrics.density).toInt()

    private fun setupListeners() {
        findViewById<View>(R.id.buttonAddMorePhotos).setOnClickListener {
            pickMultipleMedia.launch(androidx.activity.result.PickVisualMediaRequest(androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia.ImageOnly))
        }
        
        findViewById<View>(R.id.buttonCamera).setOnClickListener {
            pickMultipleMedia.launch(androidx.activity.result.PickVisualMediaRequest(androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia.ImageOnly))
        }

        // Species Toggle
        chipGroupSpecies.setOnCheckedStateChangeListener { _, checkedIds ->
            val isOther = checkedIds.contains(R.id.chipOther)
            findViewById<View>(R.id.layoutOtherSpecies).visibility = if (isOther) View.VISIBLE else View.GONE
            updateProgress()
        }

        // Date Picker
        inputVaccineDate.setOnClickListener {
            val c = Calendar.getInstance()
            DatePickerDialog(this, { _, year, month, day ->
                inputVaccineDate.setText("$day/${month + 1}/$year")
                updateProgress()
            }, c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)).show()
        }

        // Progress Watchers
        val watcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) { updateProgress() }
        }

        listOf(inputPetName, inputBreed, inputAge, inputWeight, inputDiet, inputAllergies, inputToys, inputNotes)
            .forEach { it.addTextChangedListener(watcher) }

        // Save Button
        findViewById<Button>(R.id.buttonSavePet).setOnClickListener {
            val name = inputPetName.text.toString().trim()
            if (name.isEmpty()) {
                Toast.makeText(this, "Pet name is required", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val petId: Long
            if (editingPetId != -1L) {
                val success = database.updatePet(
                    editingPetId,
                    name,
                    getSelectedSpecies(),
                    inputBreed.text.toString(),
                    inputAge.text.toString().toIntOrNull() ?: 0,
                    inputWeight.text.toString().toDoubleOrNull() ?: 0.0,
                    inputDiet.text.toString(),
                    inputVaccineDate.text.toString(),
                    switchReminder.isChecked,
                    inputAllergies.text.toString(),
                    inputToys.text.toString(),
                    inputNotes.text.toString()
                )
                petId = if (success) editingPetId else -1L
            } else {
                petId = database.savePet(
                    name,
                    getSelectedSpecies(),
                    inputBreed.text.toString(),
                    inputAge.text.toString().toIntOrNull() ?: 0,
                    inputWeight.text.toString().toDoubleOrNull() ?: 0.0,
                    inputDiet.text.toString(),
                    inputVaccineDate.text.toString(),
                    switchReminder.isChecked,
                    inputAllergies.text.toString(),
                    inputToys.text.toString(),
                    inputNotes.text.toString()
                )
            }

            if (petId != -1L) {
                database.savePetPhotos(petId, selectedPhotos)
                Toast.makeText(this, "Pet profile saved successfully!", Toast.LENGTH_SHORT).show()
                finish()
            } else {
                Toast.makeText(this, "Error saving pet profile", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun getSelectedSpecies(): String {
        return when (chipGroupSpecies.checkedChipId) {
            R.id.chipDog -> "Dog"
            R.id.chipCat -> "Cat"
            R.id.chipOther -> inputOtherSpecies.text.toString().ifEmpty { "Other" }
            else -> "Unknown"
        }
    }

    private fun updateProgress() {
        val fields = listOf(
            inputPetName.text,
            inputBreed.text,
            inputAge.text,
            inputWeight.text,
            inputDiet.text,
            inputVaccineDate.text,
            inputAllergies.text,
            inputToys.text,
            inputNotes.text
        )
        
        var count = fields.count { !it.isNullOrBlank() }
        if (chipGroupSpecies.checkedChipId != View.NO_ID) count++
        
        val total = 10
        val percent = (count * 100) / total
        
        progressCompletion.setProgress(percent, true)
        textCompletion.text = getString(R.string.add_pet_completion_text, percent)
    }
}
