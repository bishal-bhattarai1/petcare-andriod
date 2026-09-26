package com.example.petcare

import android.content.Intent
import android.location.Geocoder
import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
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
import java.util.Locale
import java.util.concurrent.Executors
import kotlin.math.roundToInt

class LocationsActivity : AppCompatActivity() {

    private lateinit var database: AuthDatabaseHelper

    private var selectedLat: Double = 0.0
    private var selectedLng: Double = 0.0
    private var pendingName: String = ""
    private var pendingCategory: String = ""
    private var pendingAddress: String = ""
    private var activeDialog: AlertDialog? = null

    private val mapPickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK && result.data != null) {
            val lat = result.data?.getDoubleExtra(LocationMapActivity.EXTRA_LATITUDE, 0.0) ?: 0.0
            val lng = result.data?.getDoubleExtra(LocationMapActivity.EXTRA_LONGITUDE, 0.0) ?: 0.0
            val address = result.data?.getStringExtra(LocationMapActivity.EXTRA_LOCATION_ADDRESS) ?: ""

            selectedLat = lat
            selectedLng = lng
            if (address.isNotBlank()) {
                pendingAddress = address
            }
            Toast.makeText(this, "Location pin selected!", Toast.LENGTH_SHORT).show()
            showAddLocationDialog(isResumeFromMap = true)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_locations)

        database = AuthDatabaseHelper(this)

        updateStatusBarIcons()

        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        toolbar.setNavigationOnClickListener { finish() }

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(android.R.id.content)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        findViewById<FloatingActionButton>(R.id.fabAddLocation).setOnClickListener {
            showAddLocationDialog(isResumeFromMap = false)
        }

        loadLocations()
    }

    override fun onResume() {
        super.onResume()
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
            radius = 20.dp().toFloat()
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
            typeface = context.figtree(android.graphics.Typeface.BOLD)
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

        val btnLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = 16.dp() }
        }

        val btnLocate = MaterialButton(this).apply {
            text = "Locate on Map"
            setTextColor(ContextCompat.getColor(this@LocationsActivity, R.color.white))
            backgroundTintList = ContextCompat.getColorStateList(this@LocationsActivity, R.color.black)
            cornerRadius = 28.dp()
            layoutParams = LinearLayout.LayoutParams(
                0,
                44.dp(),
                1f
            )
            setOnClickListener {
                val intent = Intent(this@LocationsActivity, LocationMapActivity::class.java).apply {
                    putExtra(LocationMapActivity.EXTRA_IS_PICKER, false)
                    putExtra(LocationMapActivity.EXTRA_LOCATION_NAME, loc.name)
                    putExtra(LocationMapActivity.EXTRA_LOCATION_ADDRESS, loc.address)
                    putExtra(LocationMapActivity.EXTRA_LATITUDE, loc.latitude)
                    putExtra(LocationMapActivity.EXTRA_LONGITUDE, loc.longitude)
                }
                startActivity(intent)
            }
        }
        btnLayout.addView(btnLocate)

        val btnDelete = MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
            text = "Delete"
            setTextColor(ContextCompat.getColor(this@LocationsActivity, R.color.app_accent_red))
            strokeColor = ContextCompat.getColorStateList(this@LocationsActivity, R.color.app_divider)
            cornerRadius = 28.dp()
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                44.dp()
            ).apply { marginStart = 8.dp() }
            setOnClickListener {
                showDeleteLocationConfirmation(loc)
            }
        }
        btnLayout.addView(btnDelete)

        container.addView(btnLayout)

        card.addView(container)
        return card
    }

    private fun showDeleteLocationConfirmation(loc: PetLocation) {
        com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
            .setTitle("Delete Location")
            .setMessage("Are you sure you want to remove \"${loc.name}\"?")
            .setPositiveButton("Delete") { _, _ ->
                if (database.deleteLocation(loc.id)) {
                    Toast.makeText(this, "Location removed", Toast.LENGTH_SHORT).show()
                    loadLocations()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showAddLocationDialog(isResumeFromMap: Boolean = false) {
        activeDialog?.dismiss()

        if (!isResumeFromMap) {
            selectedLat = 0.0
            selectedLng = 0.0
            pendingName = ""
            pendingCategory = ""
            pendingAddress = ""
        }

        val form = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24.dp(), 16.dp(), 24.dp(), 0)
        }
        val nameInput = EditText(this).apply {
            hint = "Location Name (e.g. Happy Vet)"
            if (pendingName.isNotBlank()) setText(pendingName)
        }
        val categoryInput = EditText(this).apply {
            hint = "Category (Vet, Salon, Park)"
            if (pendingCategory.isNotBlank()) setText(pendingCategory)
        }
        val addrInput = EditText(this).apply {
            hint = "Address or Place Name"
            if (pendingAddress.isNotBlank()) setText(pendingAddress)
        }

        val btnPickOnMap = MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
            text = if (selectedLat != 0.0 && selectedLng != 0.0) "✓ Pin Selected on Map" else "Set Location Pin on Map"
            setTextColor(ContextCompat.getColor(this@LocationsActivity, R.color.black))
            strokeColor = ContextCompat.getColorStateList(this@LocationsActivity, R.color.app_divider)
            cornerRadius = 28.dp()
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                48.dp()
            ).apply { topMargin = 12.dp() }
            setOnClickListener {
                pendingName = nameInput.text.toString().trim()
                pendingCategory = categoryInput.text.toString().trim()
                pendingAddress = addrInput.text.toString().trim()

                activeDialog?.dismiss()
                activeDialog = null

                val intent = Intent(this@LocationsActivity, LocationMapActivity::class.java).apply {
                    putExtra(LocationMapActivity.EXTRA_IS_PICKER, true)
                    putExtra(LocationMapActivity.EXTRA_LOCATION_ADDRESS, pendingAddress)
                    putExtra(LocationMapActivity.EXTRA_LATITUDE, selectedLat)
                    putExtra(LocationMapActivity.EXTRA_LONGITUDE, selectedLng)
                }
                mapPickerLauncher.launch(intent)
            }
        }

        form.addView(nameInput)
        form.addView(categoryInput)
        form.addView(addrInput)
        form.addView(btnPickOnMap)

        activeDialog = com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
            .setTitle("Save New Location")
            .setView(form)
            .setPositiveButton("Save Location") { dialog, _ ->
                val name = nameInput.text.toString().trim()
                val cat = categoryInput.text.toString().trim()
                val addr = addrInput.text.toString().trim()

                if (name.isBlank() || addr.isBlank()) {
                    Toast.makeText(this, "Name and Address are required", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                val locationId = database.saveLocationAndGetId(name, addr, cat, selectedLat, selectedLng)
                if (locationId != -1L) {
                    Toast.makeText(this, "Location saved successfully!", Toast.LENGTH_SHORT).show()
                    dialog.dismiss()
                    activeDialog = null

                    selectedLat = 0.0
                    selectedLng = 0.0
                    pendingName = ""
                    pendingCategory = ""
                    pendingAddress = ""
                    loadLocations()

                    if (selectedLat == 0.0 && selectedLng == 0.0) {
                        Executors.newSingleThreadExecutor().execute {
                            try {
                                val geocoder = Geocoder(this, Locale.getDefault())
                                @Suppress("DEPRECATION")
                                val addresses = geocoder.getFromLocationName(addr, 1)
                                if (!addresses.isNullOrEmpty()) {
                                    val lat = addresses[0].latitude
                                    val lng = addresses[0].longitude
                                    database.updateLocationCoordinates(locationId, lat, lng)
                                }
                            } catch (_: Exception) {
                            }
                        }
                    }
                } else {
                    Toast.makeText(this, "Failed to save location", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss()
                activeDialog = null
            }
            .show()
    }

    private fun Int.dp(): Int = (this * resources.displayMetrics.density).roundToInt()

    private fun updateStatusBarIcons() {
        val isDarkMode = (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES
        WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightStatusBars = !isDarkMode
    }

    private var LinearLayout.padding: Int
        get() = 0
        set(value) = setPadding(value, value, value, value)
}
