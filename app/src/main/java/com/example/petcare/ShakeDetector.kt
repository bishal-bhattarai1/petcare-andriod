package com.example.petcare

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlin.math.sqrt

/**
 * Calls [onShake] when the phone is shaken firmly. Register in onResume and unregister in onPause
 * with [start]/[stop] so it only listens while the screen is visible.
 */
class ShakeDetector(context: Context, private val onShake: () -> Unit) : SensorEventListener {

    private val sensorManager = context.getSystemService(SensorManager::class.java)
    private var lastShakeAt = 0L

    fun start() {
        sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
        }
    }

    fun stop() {
        sensorManager?.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent) {
        val (x, y, z) = Triple(event.values[0], event.values[1], event.values[2])
        val gForce = sqrt(x * x + y * y + z * z) / SensorManager.GRAVITY_EARTH
        if (gForce < SHAKE_THRESHOLD_G) return

        val now = System.currentTimeMillis()
        if (now - lastShakeAt < COOLDOWN_MS) return // One shake = one action.
        lastShakeAt = now
        onShake()
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    private companion object {
        const val SHAKE_THRESHOLD_G = 2.7f
        const val COOLDOWN_MS = 2000L
    }
}
