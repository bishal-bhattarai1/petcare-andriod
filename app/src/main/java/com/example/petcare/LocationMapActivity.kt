package com.example.petcare

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Address
import android.location.Geocoder
import android.location.LocationManager
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.floatingactionbutton.FloatingActionButton
import org.json.JSONArray
import org.json.JSONObject
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapListener
import org.osmdroid.events.ScrollEvent
import org.osmdroid.events.ZoomEvent
import org.osmdroid.tileprovider.tilesource.XYTileSource
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale
import java.util.concurrent.Executors

class LocationMapActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_IS_PICKER = "extra_is_picker"
        const val EXTRA_LOCATION_NAME = "extra_location_name"
        const val EXTRA_LOCATION_ADDRESS = "extra_location_address"
        const val EXTRA_LATITUDE = "extra_latitude"
        const val EXTRA_LONGITUDE = "extra_longitude"

        private const val PERMISSION_REQUEST_LOCATION = 1001
        private const val DEFAULT_LAT = 27.7172 // Kathmandu, Nepal central default
        private const val DEFAULT_LNG = 85.3240
        private const val USER_AGENT = "Mozilla/5.0 (Linux; Android 13; Mobile) PetCareApp/1.0"

        val FREE_OPEN_TILE_SOURCE = XYTileSource(
            "OpenStreetMap_DE",
            0,
            19,
            256,
            ".png",
            arrayOf(
                "https://a.tile.openstreetmap.de/",
                "https://b.tile.openstreetmap.de/",
                "https://c.tile.openstreetmap.de/"
            )
        )
    }

    private lateinit var mapView: MapView
    private lateinit var inputSearch: EditText
    private lateinit var btnSearch: ImageButton
    private lateinit var imgCenterPin: ImageView
    private lateinit var fabMyLocation: FloatingActionButton
    private lateinit var textSelectedName: TextView
    private lateinit var textSelectedAddress: TextView
    private lateinit var textSelectedCoords: TextView
    private lateinit var btnConfirmLocation: MaterialButton

    private var isPickerMode: Boolean = true
    private var locationName: String = ""
    private var locationAddress: String = ""
    private var currentLat: Double = DEFAULT_LAT
    private var currentLng: Double = DEFAULT_LNG

    private var mapMarker: Marker? = null
    private val executor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())
    private var geocodeRunnable: Runnable? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Configure User-Agent BEFORE loading/layout inflation to satisfy map tile policy and avoid 403 Access Blocked
        Configuration.getInstance().userAgentValue = USER_AGENT
        Configuration.getInstance().load(applicationContext, getSharedPreferences("osmdroid", Context.MODE_PRIVATE))

        enableEdgeToEdge()
        setContentView(R.layout.activity_location_map)

        updateStatusBarIcons()

        // Read Intent Arguments
        isPickerMode = intent.getBooleanExtra(EXTRA_IS_PICKER, true)
        locationName = intent.getStringExtra(EXTRA_LOCATION_NAME) ?: ""
        locationAddress = intent.getStringExtra(EXTRA_LOCATION_ADDRESS) ?: ""
        currentLat = intent.getDoubleExtra(EXTRA_LATITUDE, 0.0)
        currentLng = intent.getDoubleExtra(EXTRA_LONGITUDE, 0.0)

        initViews()
        setupMap()
        setupListeners()
    }

    private fun initViews() {
        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        toolbar.setNavigationOnClickListener { finish() }

        if (isPickerMode) {
            supportActionBar?.title = "Set Location on Map"
        } else {
            supportActionBar?.title = if (locationName.isNotBlank()) locationName else "Location Details"
        }

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(android.R.id.content)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        mapView = findViewById(R.id.mapView)
        inputSearch = findViewById(R.id.inputSearch)
        btnSearch = findViewById(R.id.btnSearch)
        imgCenterPin = findViewById(R.id.imgCenterPin)
        fabMyLocation = findViewById(R.id.fabMyLocation)
        textSelectedName = findViewById(R.id.textSelectedName)
        textSelectedAddress = findViewById(R.id.textSelectedAddress)
        textSelectedCoords = findViewById(R.id.textSelectedCoords)
        btnConfirmLocation = findViewById(R.id.btnConfirmLocation)

        imgCenterPin.visibility = if (isPickerMode) View.VISIBLE else View.GONE

        if (!isPickerMode) {
            textSelectedName.text = if (locationName.isNotBlank()) locationName else "Saved Location"
            textSelectedAddress.text = if (locationAddress.isNotBlank()) locationAddress else "Selected Location"
            btnConfirmLocation.text = "Get Directions"
        } else {
            textSelectedName.text = "Move map or search to pick location"
            if (locationAddress.isNotBlank()) {
                textSelectedAddress.text = locationAddress
                inputSearch.setText(locationAddress)
            }
            btnConfirmLocation.text = "Confirm & Set Location"
        }
    }

    private fun setupMap() {
        mapView.setTileSource(FREE_OPEN_TILE_SOURCE)
        mapView.setMultiTouchControls(true)
        mapView.controller.setZoom(16.0)

        // Determine initial center and marker
        if (currentLat != 0.0 && currentLng != 0.0) {
            val startPoint = GeoPoint(currentLat, currentLng)
            mapView.controller.setCenter(startPoint)
            updateLocationDetails(currentLat, currentLng, locationAddress)
            if (!isPickerMode) {
                addOrUpdateMarker(startPoint, locationName, locationAddress)
            }
        } else if (locationAddress.isNotBlank()) {
            searchAddress(locationAddress)
        } else {
            val startPoint = GeoPoint(DEFAULT_LAT, DEFAULT_LNG)
            mapView.controller.setCenter(startPoint)
            updateLocationDetails(DEFAULT_LAT, DEFAULT_LNG, "Kathmandu, Nepal")
            tryCenterOnDeviceLocation()
        }
    }

    private fun setupListeners() {
        btnConfirmLocation.setOnClickListener {
            if (isPickerMode) {
                val center = mapView.mapCenter as GeoPoint
                val resultIntent = Intent().apply {
                    putExtra(EXTRA_LATITUDE, center.latitude)
                    putExtra(EXTRA_LONGITUDE, center.longitude)
                    putExtra(EXTRA_LOCATION_ADDRESS, textSelectedAddress.text.toString())
                }
                setResult(RESULT_OK, resultIntent)
                finish()
            } else {
                val gmmIntentUri = if (currentLat != 0.0 && currentLng != 0.0) {
                    Uri.parse("geo:$currentLat,$currentLng?q=$currentLat,$currentLng(${Uri.encode(locationName)})")
                } else {
                    Uri.parse("geo:0,0?q=${Uri.encode(locationAddress)}")
                }
                val mapIntent = Intent(Intent.ACTION_VIEW, gmmIntentUri)
                mapIntent.setPackage("com.google.android.apps.maps")
                if (mapIntent.resolveActivity(packageManager) != null) {
                    startActivity(mapIntent)
                } else {
                    startActivity(Intent(Intent.ACTION_VIEW, gmmIntentUri))
                }
            }
        }

        fabMyLocation.setOnClickListener {
            tryCenterOnDeviceLocation()
        }

        btnSearch.setOnClickListener {
            val query = inputSearch.text.toString().trim()
            if (query.isNotBlank()) {
                searchAddress(query)
            }
        }

        inputSearch.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                val query = inputSearch.text.toString().trim()
                if (query.isNotBlank()) {
                    searchAddress(query)
                }
                true
            } else false
        }

        if (isPickerMode) {
            mapView.addMapListener(object : MapListener {
                override fun onScroll(event: ScrollEvent?): Boolean {
                    scheduleReverseGeocode()
                    return true
                }

                override fun onZoom(event: ZoomEvent?): Boolean {
                    scheduleReverseGeocode()
                    return true
                }
            })
        }
    }

    private fun scheduleReverseGeocode() {
        geocodeRunnable?.let { mainHandler.removeCallbacks(it) }
        geocodeRunnable = Runnable {
            val center = mapView.mapCenter as GeoPoint
            currentLat = center.latitude
            currentLng = center.longitude
            textSelectedCoords.text = String.format(Locale.US, "Lat: %.5f, Lng: %.5f", currentLat, currentLng)

            reverseGeocode(currentLat, currentLng)
        }
        mainHandler.postDelayed(geocodeRunnable!!, 600)
    }

    private fun reverseGeocode(lat: Double, lng: Double) {
        executor.execute {
            var addressText: String? = null
            try {
                val geocoder = Geocoder(this, Locale.getDefault())
                @Suppress("DEPRECATION")
                val addresses: List<Address>? = geocoder.getFromLocation(lat, lng, 1)
                if (!addresses.isNullOrEmpty()) {
                    val addr = addresses[0]
                    val sb = StringBuilder()
                    if (addr.maxAddressLineIndex >= 0) {
                        sb.append(addr.getAddressLine(0))
                    } else {
                        if (addr.featureName != null) sb.append(addr.featureName).append(", ")
                        if (addr.locality != null) sb.append(addr.locality)
                    }
                    addressText = sb.toString().trim().removeSuffix(",")
                }
            } catch (_: Exception) {
            }

            if (addressText.isNullOrBlank()) {
                addressText = fetchNominatimAddress(lat, lng)
            }

            if (addressText.isNullOrBlank()) {
                addressText = String.format(Locale.US, "Selected Point (%.5f, %.5f)", lat, lng)
            }

            val finalAddress = addressText
            mainHandler.post {
                textSelectedAddress.text = finalAddress
            }
        }
    }

    private fun fetchNominatimAddress(lat: Double, lng: Double): String? {
        return try {
            val url = URL(String.format(Locale.US, "https://nominatim.openstreetmap.org/reverse?format=json&lat=%.6f&lon=%.6f", lat, lng))
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.setRequestProperty("User-Agent", USER_AGENT)
            conn.connectTimeout = 5000
            conn.readTimeout = 5000
            if (conn.responseCode == 200) {
                val reader = BufferedReader(InputStreamReader(conn.inputStream))
                val response = reader.readText()
                reader.close()
                val jsonObj = JSONObject(response)
                if (jsonObj.has("display_name")) jsonObj.getString("display_name") else null
            } else null
        } catch (_: Exception) {
            null
        }
    }

    private fun searchAddress(query: String) {
        executor.execute {
            var point: GeoPoint? = null
            var fullAddr: String? = null

            try {
                val geocoder = Geocoder(this, Locale.getDefault())
                @Suppress("DEPRECATION")
                val addresses: List<Address>? = geocoder.getFromLocationName(query, 1)
                if (!addresses.isNullOrEmpty()) {
                    val location = addresses[0]
                    point = GeoPoint(location.latitude, location.longitude)
                    fullAddr = if (location.maxAddressLineIndex >= 0) location.getAddressLine(0) else query
                }
            } catch (_: Exception) {
            }

            if (point == null) {
                val result = searchNominatim(query)
                if (result != null) {
                    point = result.first
                    fullAddr = result.second
                }
            }

            if (point != null) {
                val targetPoint = point
                val targetAddr = fullAddr ?: query
                mainHandler.post {
                    mapView.controller.animateTo(targetPoint)
                    currentLat = targetPoint.latitude
                    currentLng = targetPoint.longitude
                    updateLocationDetails(currentLat, currentLng, targetAddr)
                    if (!isPickerMode) {
                        addOrUpdateMarker(targetPoint, locationName.ifBlank { query }, targetAddr)
                    }
                }
            } else {
                mainHandler.post {
                    Toast.makeText(this, "Location not found for \"$query\"", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun searchNominatim(query: String): Pair<GeoPoint, String>? {
        return try {
            val encodedQuery = URLEncoder.encode(query, "UTF-8")
            val url = URL("https://nominatim.openstreetmap.org/search?format=json&q=$encodedQuery&limit=1")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.setRequestProperty("User-Agent", USER_AGENT)
            conn.connectTimeout = 5000
            conn.readTimeout = 5000
            if (conn.responseCode == 200) {
                val reader = BufferedReader(InputStreamReader(conn.inputStream))
                val response = reader.readText()
                reader.close()
                val jsonArr = JSONArray(response)
                if (jsonArr.length() > 0) {
                    val jsonObj = jsonArr.getJSONObject(0)
                    val lat = jsonObj.getDouble("lat")
                    val lon = jsonObj.getDouble("lon")
                    val displayName = jsonObj.optString("display_name", query)
                    Pair(GeoPoint(lat, lon), displayName)
                } else null
            } else null
        } catch (_: Exception) {
            null
        }
    }

    private fun updateLocationDetails(lat: Double, lng: Double, address: String) {
        currentLat = lat
        currentLng = lng
        textSelectedCoords.text = String.format(Locale.US, "Lat: %.5f, Lng: %.5f", lat, lng)
        textSelectedAddress.text = if (address.isNotBlank()) address else "Selected Location"
    }

    private fun addOrUpdateMarker(point: GeoPoint, title: String, snippet: String) {
        if (point.latitude == 0.0 && point.longitude == 0.0) return

        if (mapMarker == null) {
            mapMarker = Marker(mapView).apply {
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                mapView.overlays.add(this)
            }
        }
        mapMarker?.apply {
            position = point
            this.title = title.ifBlank { "Saved Location" }
            this.snippet = snippet.ifBlank { "Location Pin" }
            showInfoWindow()
        }
        mapView.invalidate()
    }

    private fun tryCenterOnDeviceLocation() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
                PERMISSION_REQUEST_LOCATION
            )
            return
        }

        try {
            val lm = getSystemService(Context.LOCATION_SERVICE) as LocationManager
            val gpsLoc = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER)
            val netLoc = lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
            val bestLoc = gpsLoc ?: netLoc

            if (bestLoc != null) {
                val point = GeoPoint(bestLoc.latitude, bestLoc.longitude)
                mapView.controller.animateTo(point)
                currentLat = bestLoc.latitude
                currentLng = bestLoc.longitude
                scheduleReverseGeocode()
            } else {
                Toast.makeText(this, "Detecting location... Scroll or search manually", Toast.LENGTH_SHORT).show()
            }
        } catch (_: Exception) {
            Toast.makeText(this, "Unable to access current location", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST_LOCATION && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            tryCenterOnDeviceLocation()
        }
    }

    override fun onResume() {
        super.onResume()
        mapView.onResume()
    }

    override fun onPause() {
        super.onPause()
        mapView.onPause()
    }

    override fun onDestroy() {
        super.onDestroy()
        executor.shutdown()
    }

    private fun updateStatusBarIcons() {
        val isDarkMode = (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES
        WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightStatusBars = !isDarkMode
    }
}
