package com.example.petcare

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.ContactsContract
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.ChipGroup
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.switchmaterial.SwitchMaterial

class DelegateContactActivity : AppCompatActivity() {

    private lateinit var database: AuthDatabaseHelper
    private lateinit var sessionManager: SessionManager

    private lateinit var textSelectedPetName: TextView
    private lateinit var textContactName: TextView
    private lateinit var textContactPhone: TextView
    private lateinit var chipGroupCategory: ChipGroup
    private lateinit var switchCareNotes: SwitchMaterial
    private lateinit var textMessagePreview: TextView
    private lateinit var buttonSendSMS: MaterialButton
    private lateinit var editCustomInstructions: com.google.android.material.textfield.TextInputEditText

    private var petOptions: List<PetOption> = emptyList()
    private var selectedPet: PetOption? = null
    private var selectedPhoneNumber: String = ""

    private val pickContactLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val contactUri = result.data?.data ?: return@registerForActivityResult
            val projection = arrayOf(
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NUMBER
            )
            contentResolver.query(contactUri, projection, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val name = cursor.getString(cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME))
                    val number = cursor.getString(cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.NUMBER))
                    
                    textContactName.text = name
                    textContactPhone.text = number
                    selectedPhoneNumber = number
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_delegate_contact)

        database = AuthDatabaseHelper(this)
        sessionManager = SessionManager(this)

        WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightStatusBars = true

        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        toolbar.setNavigationOnClickListener { finish() }

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(android.R.id.content)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        initViews()
        loadPets()
        setupListeners()
        updateMessagePreview()
    }

    private fun initViews() {
        textSelectedPetName = findViewById(R.id.textSelectedPetName)
        textContactName = findViewById(R.id.textContactName)
        textContactPhone = findViewById(R.id.textContactPhone)
        chipGroupCategory = findViewById(R.id.chipGroupCategory)
        switchCareNotes = findViewById(R.id.switchCareNotes)
        textMessagePreview = findViewById(R.id.textMessagePreview)
        buttonSendSMS = findViewById(R.id.buttonSendSMS)
        editCustomInstructions = findViewById(R.id.editCustomInstructions)

        // Prefill default delegate contact if available
        val defaultContact = sessionManager.getDefaultDelegateContact()
        if (defaultContact.isNotBlank()) {
            textContactName.text = "Default Delegate Contact"
            textContactPhone.text = defaultContact
            selectedPhoneNumber = defaultContact
        }
    }

    private fun loadPets() {
        petOptions = database.getPetOptions()
        if (petOptions.isNotEmpty()) {
            selectedPet = petOptions.first()
            textSelectedPetName.text = selectedPet?.name
        } else {
            textSelectedPetName.text = "No pets available"
        }
    }

    private fun setupListeners() {
        findViewById<View>(R.id.cardSelectPet).setOnClickListener {
            if (petOptions.isEmpty()) {
                Toast.makeText(this, "Please add a pet first", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val petNames = petOptions.map { it.name }.toTypedArray()
            val checkedItem = petOptions.indexOf(selectedPet)

            MaterialAlertDialogBuilder(this)
                .setTitle("Select Pet")
                .setSingleChoiceItems(petNames, checkedItem) { dialog, which ->
                    selectedPet = petOptions[which]
                    textSelectedPetName.text = selectedPet?.name
                    updateMessagePreview()
                    dialog.dismiss()
                }
                .show()
        }

        findViewById<View>(R.id.cardSelectContact).setOnClickListener {
            val intent = Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI)
            pickContactLauncher.launch(intent)
        }

        chipGroupCategory.setOnCheckedStateChangeListener { _, _ ->
            updateMessagePreview()
        }

        switchCareNotes.setOnCheckedChangeListener { _, _ ->
            updateMessagePreview()
        }

        editCustomInstructions.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
                updateMessagePreview()
            }
        })

        buttonSendSMS.setOnClickListener {
            if (selectedPet == null) {
                Toast.makeText(this, "Please select a pet", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (selectedPhoneNumber.isBlank()) {
                Toast.makeText(this, "Please select a recipient contact", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val messageText = textMessagePreview.text.toString()
            try {
                val intent = Intent(Intent.ACTION_SENDTO).apply {
                    data = Uri.parse("smsto:$selectedPhoneNumber")
                    putExtra("sms_body", messageText)
                }
                startActivity(intent)
            } catch (e: Exception) {
                Toast.makeText(this, "Unable to open SMS application", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun updateMessagePreview() {
        val pet = selectedPet
        if (pet == null) {
            textMessagePreview.text = "Please select a pet to preview the message text."
            return
        }

        val builder = StringBuilder()
        builder.append("Hi! Here are the care details for ${pet.name}:\n\n")

        val checkedChipId = chipGroupCategory.checkedChipId
        val allTasks = database.getCareTasks(pet.id)

        when (checkedChipId) {
            R.id.chipChecklist -> {
                builder.append("📋 Care Checklist:\n")
                val pendingTasks = allTasks.filter { !it.isCompleted }
                if (pendingTasks.isNotEmpty()) {
                    pendingTasks.forEach { task ->
                        builder.append("- ${task.description} [${task.scheduledTime.ifBlank { "Anytime" }}]\n")
                    }
                } else {
                    builder.append("All scheduled care tasks are currently completed!\n")
                }
            }
            R.id.chipFeeding -> {
                builder.append("🍖 Feeding Schedule:\n")
                val feedingTasks = allTasks.filter { it.category.contains("Feed", ignoreCase = true) || it.description.contains("feed", ignoreCase = true) || it.description.contains("food", ignoreCase = true) }
                if (feedingTasks.isNotEmpty()) {
                    feedingTasks.forEach { task ->
                        builder.append("- ${task.description} at ${task.scheduledTime.ifBlank { "Scheduled time" }}\n")
                    }
                } else {
                    builder.append("- Regular feeding morning and evening.\n")
                }
            }
            R.id.chipMedication -> {
                builder.append("💊 Medication Info:\n")
                val medTasks = allTasks.filter { it.category.contains("Med", ignoreCase = true) || it.category.contains("Vet", ignoreCase = true) || it.description.contains("med", ignoreCase = true) || it.description.contains("pill", ignoreCase = true) }
                if (medTasks.isNotEmpty()) {
                    medTasks.forEach { task ->
                        builder.append("- ${task.description} [Time: ${task.scheduledTime.ifBlank { "As prescribed" }}]\n")
                    }
                } else {
                    builder.append("No active medications listed for today.\n")
                }
            }
            R.id.chipEmergencyHealth -> {
                builder.append("🚨 Emergency & Health Summary:\n")
                var species = ""
                var breed = ""
                var age = 0
                var weight = 0.0
                var vaccineDate = ""
                var allergies = ""
                try {
                    val cursor = database.readableDatabase.query(
                        "pets",
                        arrayOf("species", "breed", "age", "weight", "vaccine_date", "allergies"),
                        "id = ?",
                        arrayOf(pet.id.toString()),
                        null, null, null
                    )
                    cursor?.use {
                        if (it.moveToFirst()) {
                            species = it.getString(it.getColumnIndexOrThrow("species")).orEmpty()
                            breed = it.getString(it.getColumnIndexOrThrow("breed")).orEmpty()
                            age = it.getInt(it.getColumnIndexOrThrow("age"))
                            weight = it.getDouble(it.getColumnIndexOrThrow("weight"))
                            vaccineDate = it.getString(it.getColumnIndexOrThrow("vaccine_date")).orEmpty()
                            allergies = it.getString(it.getColumnIndexOrThrow("allergies")).orEmpty()
                        }
                    }
                } catch (_: Exception) {}

                builder.append("- Type: $species\n")
                if (breed.isNotBlank()) builder.append("- Breed: $breed\n")
                if (age > 0) builder.append("- Age: $age years old\n")
                if (weight > 0.0) builder.append("- Weight: $weight kg\n")
                if (allergies.isNotBlank()) builder.append("- Allergies: $allergies\n")
                if (vaccineDate.isNotBlank()) builder.append("- Last/Next Vaccine: $vaccineDate\n")
            }
        }

        if (switchCareNotes.isChecked) {
            builder.append("\n📝 Additional Care Notes:\n")
            var diet = ""
            var notes = ""
            try {
                val cursor = database.readableDatabase.query(
                    "pets",
                    arrayOf("diet", "notes"),
                    "id = ?",
                    arrayOf(pet.id.toString()),
                    null, null, null
                )
                cursor?.use {
                    if (it.moveToFirst()) {
                        diet = it.getString(it.getColumnIndexOrThrow("diet")).orEmpty()
                        notes = it.getString(it.getColumnIndexOrThrow("notes")).orEmpty()
                    }
                }
            } catch (_: Exception) {}

            if (diet.isNotBlank()) builder.append("Diet: $diet\n")
            if (notes.isNotBlank()) builder.append("Notes: $notes\n")
            if (diet.isBlank() && notes.isBlank()) builder.append("No dietary restrictions or custom notes specified.\n")
        }

        val customText = editCustomInstructions.text?.toString()?.trim().orEmpty()
        if (customText.isNotBlank()) {
            builder.append("\n⚠️ Special Instructions:\n\"$customText\"\n")
        }

        val senderName = sessionManager.getUserName() ?: "Your PetCare Companion"
        builder.append("\nRegards,\n$senderName\n(Sent via PetCare)")

        textMessagePreview.text = builder.toString()
    }
}
