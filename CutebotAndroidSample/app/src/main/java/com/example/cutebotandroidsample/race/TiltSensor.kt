package com.example.cutebotandroidsample.race

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlin.math.abs

/**
 * Turns phone roll into a steering axis for tilt-drive mode.
 *
 * The raw accelerometer is noisy at the frequency a hand shakes, so readings run through a
 * one-pole low-pass filter before being normalised. [tare] captures the current attitude as
 * centre, which means the driver can hold the phone at whatever angle is comfortable
 * — including leaning over a track table — without biasing the steering.
 */
class TiltSensor(context: Context) : SensorEventListener {

    private val manager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    private val accelerometer = manager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

    /** Smoothed gravity component along the device's X axis, in m/s². */
    private var filteredX = 0f
    private var neutralX = 0f
    private var hasSample = false

    /** Normalised steering, -1f (left) .. +1f (right). */
    @Volatile
    var steer: Float = 0f
        private set

    val available: Boolean get() = accelerometer != null

    fun start() {
        val sensor = accelerometer ?: return
        manager?.registerListener(this, sensor, SensorManager.SENSOR_DELAY_GAME)
    }

    fun stop() {
        manager?.unregisterListener(this)
        steer = 0f
    }

    /** Re-centres the steering on the phone's current attitude. */
    fun tare() {
        neutralX = filteredX
        steer = 0f
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type != Sensor.TYPE_ACCELEROMETER) return
        val x = event.values[0]
        filteredX = if (hasSample) filteredX + SMOOTHING * (x - filteredX) else x
        if (!hasSample) {
            hasSample = true
            neutralX = filteredX
        }
        // Negated so tilting the phone left steers left.
        val offset = -(filteredX - neutralX)
        steer = (offset / FULL_LOCK_MS2).coerceIn(-1f, 1f).let { if (abs(it) < 0.05f) 0f else it }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    private companion object {
        /** Low-pass coefficient; lower is smoother but laggier. */
        const val SMOOTHING = 0.25f

        /** Roll producing full steering lock — ~35 degrees from neutral. */
        const val FULL_LOCK_MS2 = 5.6f
    }
}
