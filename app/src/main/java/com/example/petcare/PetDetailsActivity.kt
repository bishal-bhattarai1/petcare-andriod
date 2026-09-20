package com.example.petcare

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

class PetDetailsActivity : AppCompatActivity() {

    private lateinit var database: AuthDatabaseHelper

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_pet_details)

        database = AuthDatabaseHelper(this)

        WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightStatusBars = true

        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        toolbar.setNavigationOnClickListener { finish() }

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(android.R.id.content)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        val petId = intent.getLongExtra("EXTRA_PET_ID", -1L)
        if (petId != -1L) {
            loadPetDetails(petId)
            loadPhotos(petId)
            loadHealthcareHistory(petId)
            
            findViewById<View>(R.id.buttonAddHistory).setOnClickListener {
                showAddHistoryDialog(petId)
            }
            
            findViewById<View>(R.id.buttonEditPet).setOnClickListener {
                val intent = Intent(this, AddEditPetActivity::class.java).apply {
                    putExtra("EXTRA_PET_ID", petId)
                }
                startActivity(intent)
            }
        } else {
            finish()
        }
    }

    private fun loadPetDetails(petId: Long) {
        try {
            val cursor = database.readableDatabase.query(
                "pets",
                null,
                "id = ?",
                arrayOf(petId.toString()),
                null, null, null, "1"
            )

            cursor?.use {
                if (it.moveToFirst()) {
                    val name = it.getString(it.getColumnIndexOrThrow("name")).orEmpty()
                    val species = it.getString(it.getColumnIndexOrThrow("species")).orEmpty()
                    val breed = it.getString(it.getColumnIndexOrThrow("breed")).orEmpty()
                    val age = it.getInt(it.getColumnIndexOrThrow("age"))
                    val weight = it.getDouble(it.getColumnIndexOrThrow("weight"))
                    val diet = it.getString(it.getColumnIndexOrThrow("diet")).orEmpty()
                    val vaccineDate = it.getString(it.getColumnIndexOrThrow("vaccine_date")).orEmpty()
                    val allergies = it.getString(it.getColumnIndexOrThrow("allergies")).orEmpty()
                    val toys = it.getString(it.getColumnIndexOrThrow("toys")).orEmpty()
                    val notes = it.getString(it.getColumnIndexOrThrow("notes")).orEmpty()

                    findViewById<TextView>(R.id.textPetNameHeader).text = name
                    findViewById<TextView>(R.id.textPetBreedHeader).text = "$species - $breed"
                    findViewById<TextView>(R.id.textPetAgeVal).text = "$age yrs"
                    findViewById<TextView>(R.id.textPetWeightVal).text = "$weight kg"
                    
                    findViewById<TextView>(R.id.textPetDiet).text = if (diet.isNotBlank()) diet else "No special dietary options entered."
                    findViewById<TextView>(R.id.textPetAllergies).text = if (allergies.isNotBlank()) allergies else "None reported"
                    findViewById<TextView>(R.id.textPetToys).text = if (toys.isNotBlank()) toys else "None specified"
                    findViewById<TextView>(R.id.textPetVaccine).text = if (vaccineDate.isNotBlank()) vaccineDate else "Not scheduled"
                    findViewById<TextView>(R.id.textPetNotes).text = if (notes.isNotBlank()) notes else "No custom behavioral observations listed."
                }
            }
        } catch (_: Exception) {
            finish()
        }
    }

    private fun loadPhotos(petId: Long) {
        val photos = database.getPetPhotos(petId)
        val layout = findViewById<android.widget.LinearLayout>(R.id.layoutDetailsPhotos)
        layout.removeAllViews()
        
        photos.forEach { uri ->
            val img = com.google.android.material.imageview.ShapeableImageView(this).apply {
                layoutParams = android.widget.LinearLayout.LayoutParams(120.dp(), 120.dp()).apply { marginEnd = 12.dp() }
                scaleType = android.widget.ImageView.ScaleType.CENTER_CROP
                shapeAppearanceModel = com.google.android.material.shape.ShapeAppearanceModel.builder()
                    .setAllCorners(com.google.android.material.shape.CornerFamily.ROUNDED, 12.dp().toFloat())
                    .build()
                setImageURI(android.net.Uri.parse(uri))
            }
            layout.addView(img)
        }
    }

    private fun loadHealthcareHistory(petId: Long) {
        val list = database.getHealthcareHistory(petId)
        val layout = findViewById<android.widget.LinearLayout>(R.id.layoutHealthcareHistory)
        layout.removeAllViews()
        
        list.forEach { record ->
            val card = com.google.android.material.card.MaterialCardView(this).apply {
                layoutParams = android.widget.LinearLayout.LayoutParams(
                    android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                    android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = 10.dp() }
                radius = 12.dp().toFloat()
                cardElevation = 0f
                strokeWidth = 1.dp()
                strokeColor = androidx.core.content.ContextCompat.getColor(this@PetDetailsActivity, R.color.app_divider)
                setCardBackgroundColor(androidx.core.content.ContextCompat.getColor(this@PetDetailsActivity, R.color.card_bg))
            }
            
            val container = android.widget.LinearLayout(this).apply {
                orientation = android.widget.LinearLayout.VERTICAL
                setPadding(14.dp(), 12.dp(), 14.dp(), 12.dp())
            }
            
            val rowTitle = android.widget.TextView(this).apply {
                text = "${record.type} - ${record.date}"
                setTextColor(androidx.core.content.ContextCompat.getColor(this@PetDetailsActivity, R.color.app_text_primary))
                textSize = 14f
                typeface = android.graphics.Typeface.DEFAULT_BOLD
            }
            container.addView(rowTitle)
            
            if (record.notes.isNotBlank()) {
                val notes = android.widget.TextView(this).apply {
                    text = record.notes
                    setTextColor(androidx.core.content.ContextCompat.getColor(this@PetDetailsActivity, R.color.app_text_secondary))
                    textSize = 12f
                    setPadding(0, 4.dp(), 0, 0)
                }
                container.addView(notes)
            }
            
            card.addView(container)
            layout.addView(card)
        }
    }

    private fun showAddHistoryDialog(petId: Long) {
        val form = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(24.dp(), 8.dp(), 24.dp(), 0)
        }
        val typeInput = android.widget.EditText(this).apply { hint = "Type (Vaccination, Exam, Surgery)" }
        val dateInput = android.widget.EditText(this).apply {
            hint = "Date (DD/MM/YYYY)"
            val c = java.util.Calendar.getInstance()
            setOnClickListener {
                android.app.DatePickerDialog(this@PetDetailsActivity, { _, y, m, d ->
                    setText("$d/${m + 1}/$y")
                }, c.get(java.util.Calendar.YEAR), c.get(java.util.Calendar.MONTH), c.get(java.util.Calendar.DAY_OF_MONTH)).show()
            }
            isFocusable = false
        }
        val notesInput = android.widget.EditText(this).apply { hint = "Notes (Result, Clinic name)" }
        
        form.addView(typeInput)
        form.addView(dateInput)
        form.addView(notesInput)

        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Log Healthcare Record")
            .setView(form)
            .setPositiveButton("Save") { _, _ ->
                val type = typeInput.text.toString().trim()
                val date = dateInput.text.toString().trim()
                val notes = notesInput.text.toString().trim()
                
                if (type.isNotBlank() && date.isNotBlank()) {
                    if (database.saveHealthcareRecord(petId, type, date, notes)) {
                        NotificationHelper(this@PetDetailsActivity).showTaskNotification(
                            System.currentTimeMillis(),
                            "Healthcare Logged!",
                            "$type recorded for pet."
                        )
                        loadHealthcareHistory(petId)
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun Int.dp(): Int = (this * resources.displayMetrics.density).toInt()
}
