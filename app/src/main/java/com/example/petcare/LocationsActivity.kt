package com.example.petcare

import androidx.lifecycle.lifecycleScope
import com.example.petcare.data.PlaceRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
import kotlin.math.roundToInt

class LocationsActivity : AppCompatActivity() {

    private lateinit var places: PlaceRepository

    private var selectedLat: Double = 0.0
    private var selectedLng: Double = 0.0
    private var pendingName: String = ""
    private var pendingCategory: String = ""
    /** null = show all categories. */
    private var placeFilter: PlaceCategory? = null
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

        places = PlaceRepository(this)

        updateStatusBarIcons()

        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        toolbar.setNavigationOnClickListener { finish() }

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(android.R.id.content)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        setupPlaceFilter()

        findViewById<FloatingActionButton>(R.id.fabAddLocation).setOnClickListener {
            showAddLocationDialog(isResumeFromMap = false)
        }

        loadLocations()
    }

    override fun onResume() {
        super.onResume()
        loadLocations()
    }

    private fun setupPlaceFilter() {
        val group = findViewById<com.google.android.material.chip.ChipGroup>(R.id.chipGroupPlaceFilter)
        (listOf<PlaceCategory?>(null) + PlaceCategory.entries).forEach { category ->
            val chip = placeChip(category?.label ?: "All", category)
            group.addView(chip)
            if (category == null) group.check(chip.id)
        }
        group.setOnCheckedStateChangeListener { g, ids ->
            val chip = ids.firstOrNull()?.let { g.findViewById<com.google.android.material.chip.Chip>(it) }
                ?: return@setOnCheckedStateChangeListener
            placeFilter = chip.tag as? PlaceCategory
            loadLocations()
        }
    }

    private fun placeChip(label: String, category: PlaceCategory?) =
        com.google.android.material.chip.Chip(this).apply {
            id = View.generateViewId()
            tag = category
            text = label
            isCheckable = true
            isCheckedIconVisible = false
            chipStrokeWidth = resources.displayMetrics.density
            setTextColor(ContextCompat.getColorStateList(this@LocationsActivity, R.color.chip_selectable_text))
            chipBackgroundColor = ContextCompat.getColorStateList(this@LocationsActivity, R.color.chip_selectable_bg)
            chipStrokeColor = ContextCompat.getColorStateList(this@LocationsActivity, R.color.chip_selectable_stroke)
            category?.let {
                chipIcon = ContextCompat.getDrawable(this@LocationsActivity, it.icon)
                chipIconTint = ContextCompat.getColorStateList(this@LocationsActivity, R.color.chip_selectable_text)
                chipIconSize = 16 * resources.displayMetrics.density
            }
        }

    /** Loads the user's saved places off the main thread, then shows them. */
    private fun loadLocations() {
        lifecycleScope.launch {
            showLocations(withContext(Dispatchers.IO) { places.getLocations() })
        }
    }

    private fun showLocations(allLocations: List<PetLocation>) {
        val locations = allLocations
            .filter { placeFilter == null || PlaceCategory.from(it.category) == placeFilter }
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

        val category = PlaceCategory.from(loc.category)
        val catText = TextView(this).apply {
            text = category.label
            setTextColor(ContextCompat.getColor(this@LocationsActivity, category.fg))
            textSize = 12f
            typeface = context.figtree(android.graphics.Typeface.BOLD)
            setBackgroundResource(R.drawable.bg_task_badge_done)
            backgroundTintList = ContextCompat.getColorStateList(this@LocationsActivity, category.bg)
            setPadding(10.dp(), 4.dp(), 10.dp(), 4.dp())
            compoundDrawablePadding = 4.dp()
            setCompoundDrawablesRelativeWithIntrinsicBounds(
                ContextCompat.getDrawable(this@LocationsActivity, category.icon)?.mutate()?.apply {
                    setBounds(0, 0, 14.dp(), 14.dp())
                    setTint(ContextCompat.getColor(this@LocationsActivity, category.fg))
                }, null, null, null
            )
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = 6.dp() }
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
                lifecycleScope.launch {
                    if (withContext(Dispatchers.IO) { places.deleteLocation(loc.id) }) {
                        Toast.makeText(this@LocationsActivity, "Location removed", Toast.LENGTH_SHORT).show()
                        loadLocations()
                    }
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
        val categoryGroup = com.google.android.material.chip.ChipGroup(this).apply {
            isSingleSelection = true
            isSelectionRequired = true
            setPadding(0, 8.dp(), 0, 4.dp())
            val initial = if (pendingCategory.isNotBlank()) PlaceCategory.from(pendingCategory) else PlaceCategory.VET
            PlaceCategory.entries.forEach { category ->
                val chip = placeChip(category.label, category)
                addView(chip)
                if (category == initial) check(chip.id)
            }
        }
        fun selectedCategory(): String =
            (categoryGroup.findViewById<com.google.android.material.chip.Chip>(categoryGroup.checkedChipId)?.tag as? PlaceCategory
                ?: PlaceCategory.OTHER).label
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
                pendingCategory = selectedCategory()
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
        form.addView(TextView(this).apply {
            text = "Type of place"
            setTextColor(ContextCompat.getColor(this@LocationsActivity, R.color.app_text_secondary))
            textSize = 12f
            setPadding(4.dp(), 12.dp(), 0, 0)
        })
        form.addView(categoryGroup)
        form.addView(addrInput)
        form.addView(btnPickOnMap)

        activeDialog = com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
            .setTitle("Save New Location")
            .setView(form)
            .setPositiveButton("Save Location") { dialog, _ ->
                val name = nameInput.text.toString().trim()
                val cat = selectedCategory()
                val addr = addrInput.text.toString().trim()

                if (name.isBlank() || addr.isBlank()) {
                    Toast.makeText(this, "Name and Address are required", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                // Capture the map pin BEFORE the form is reset below.
                val pickedLat = selectedLat
                val pickedLng = selectedLng
                lifecycleScope.launch {
                    val locationId = withContext(Dispatchers.IO) {
                        places.saveLocationAndGetId(name, addr, cat, pickedLat, pickedLng)
                    }
                    if (locationId == -1L) {
                        Toast.makeText(this@LocationsActivity, "Failed to save location", Toast.LENGTH_SHORT).show()
                        return@launch
                    }
                    Toast.makeText(this@LocationsActivity, "Location saved successfully!", Toast.LENGTH_SHORT).show()
                    dialog.dismiss()
                    activeDialog = null

                    selectedLat = 0.0
                    selectedLng = 0.0
                    pendingName = ""
                    pendingCategory = ""
                    pendingAddress = ""
                    loadLocations()

                    // No pin was dropped on the map: look the address up to get coordinates.
                    if (pickedLat == 0.0 && pickedLng == 0.0) {
                        withContext(Dispatchers.IO) {
                            try {
                                val geocoder = Geocoder(this@LocationsActivity, Locale.getDefault())
                                @Suppress("DEPRECATION")
                                val addresses = geocoder.getFromLocationName(addr, 1)
                                if (!addresses.isNullOrEmpty()) {
                                    places.updateLocationCoordinates(locationId, addresses[0].latitude, addresses[0].longitude)
                                }
                            } catch (_: Exception) {
                            }
                        }
                    }
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
