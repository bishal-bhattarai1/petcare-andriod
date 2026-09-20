package com.example.petcare

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import kotlin.math.roundToInt

class LocationsActivity : AppCompatActivity() {

    private lateinit var database: AuthDatabaseHelper

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_locations)

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

        findViewById<FloatingActionButton>(R.id.fabAddLocation).setOnClickListener {
            showAddLocationDialog()
        }

        loadLocations()
    }

    private fun loadLocations() {
        val locations = database.getLocations()
        val layout = findViewById<LinearLayout>(R.id.layoutLocationList)
        layout.removeAllViews()
        
        locations.forEach { loc ->
            layout.addView(createLocationRow(loc))
        }
        
        findViewById<TextView>(R.id.textEmptyLocations).visibility = 
            if (locations.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun createLocationRow(loc: PetLocation): View {
        val card = MaterialCardView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = 12.dp() }
            radius = 14.dp().toFloat()
            cardElevation = 0f
            strokeWidth = 1.dp()
            strokeColor = ContextCompat.getColor(this@LocationsActivity, R.color.app_divider)
            setCardBackgroundColor(ContextCompat.getColor(this@LocationsActivity, R.color.card_bg))
        }

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            padding = 16.dp()
        }

        val nameText = TextView(this).apply {
            text = loc.name
            setTextColor(ContextCompat.getColor(this@LocationsActivity, R.color.app_text_primary))
            textSize = 16f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        }
        container.addView(nameText)

        val catText = TextView(this).apply {
            text = loc.category
            setTextColor(ContextCompat.getColor(this@LocationsActivity, R.color.app_text_secondary))
            textSize = 12f
            setPadding(0, 2.dp(), 0, 0)
        }
        container.addView(catText)

        val addrText = TextView(this).apply {
            text = loc.address
            setTextColor(ContextCompat.getColor(this@LocationsActivity, R.color.app_text_secondary))
            textSize = 14f
            setPadding(0, 8.dp(), 0, 0)
        }
        container.addView(addrText)

        val btnLocate = MaterialButton(this).apply {
            text = "Locate on Map"
            setTextColor(ContextCompat.getColor(this@LocationsActivity, R.color.white))
            backgroundTintList = ContextCompat.getColorStateList(this@LocationsActivity, R.color.black)
            cornerRadius = 8.dp()
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                44.dp()
            ).apply { topMargin = 16.dp() }
            setOnClickListener {
                val gmmIntentUri = Uri.parse("geo:0,0?q=${Uri.encode(loc.address)}")
                val mapIntent = Intent(Intent.ACTION_VIEW, gmmIntentUri)
                mapIntent.setPackage("com.google.android.apps.maps")
                if (mapIntent.resolveActivity(packageManager) != null) {
                    startActivity(mapIntent)
                } else {
                    startActivity(Intent(Intent.ACTION_VIEW, gmmIntentUri))
                }
            }
        }
        container.addView(btnLocate)

        card.addView(container)
        return card
    }

    private fun showAddLocationDialog() {
        val form = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24.dp(), 8.dp(), 24.dp(), 0)
        }
        val nameInput = EditText(this).apply { hint = "Location Name (e.g. Happy Vet)" }
        val categoryInput = EditText(this).apply { hint = "Category (Vet, Salon, Park)" }
        val addrInput = EditText(this).apply { hint = "Address or Place Name" }
        
        form.addView(nameInput)
        form.addView(categoryInput)
        form.addView(addrInput)

        AlertDialog.Builder(this)
            .setTitle("Save New Location")
            .setView(form)
            .setPositiveButton("Save") { _, _ ->
                val name = nameInput.text.toString().trim()
                val cat = categoryInput.text.toString().trim()
                val addr = addrInput.text.toString().trim()
                
                if (name.isBlank() || addr.isBlank()) {
                    Toast.makeText(this, "Name and Address are required", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                
                if (database.saveLocation(name, addr, cat)) {
                    loadLocations()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun Int.dp(): Int = (this * resources.displayMetrics.density).roundToInt()
    
    private var LinearLayout.padding: Int
        get() = 0
        set(value) = setPadding(value, value, value, value)
}
