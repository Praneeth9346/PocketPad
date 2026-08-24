package com.aistudio.pocketpad.sensor

import android.os.Build
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.view.Surface
import android.view.WindowManager
import com.aistudio.pocketpad.filter.OneEuroFilter
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sign

class MotionSensorManager(
    private val context: Context,
    private val onSteeringUpdated: (normalizedSteer: Float, visualAngleDeg: Float, rawTiltDeg: Float) -> Unit
) : SensorEventListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
    private val accelerometer: Sensor? = sensorManager?.getDefaultSensor(Sensor.TYPE_GRAVITY)
        ?: sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

    private val oneEuro = OneEuroFilter(minCutoff = 0.85, beta = 0.015, dCutoff = 1.0)

    var isEnabled: Boolean = false
        private set

    var calibratedCenter: Float = 0f
    var manualTrimOffset: Float = 0f
    var invertSteering: Boolean = false
    var maxSteeringAngle: Int = 45 // degrees for 100% lock
    var steeringSensitivity: Float = 2.89f // Multiplier: effective full lock = maxSteeringAngle / sensitivity ≈ 15.6°
    var antiDeadzone: Float = 0.10f // Game deadband bypass (10%)
    var curveExponent: Float = 1.0f
    var sensorDeadzone: Float = 0.03f // Tremor guard: prevents noise-driven anti-deadzone activation

    private var smoothedAngle: Float = 0f
    private var latestRawAngle: Float = 0f

    fun start() {
        if (isEnabled || accelerometer == null || sensorManager == null) return
        isEnabled = true
        oneEuro.reset()
        smoothedAngle = 0f
        sensorManager.registerListener(
            this,
            accelerometer,
            SensorManager.SENSOR_DELAY_GAME
        )
    }

    fun stop() {
        if (!isEnabled) return
        isEnabled = false
        sensorManager?.unregisterListener(this)
        smoothedAngle = 0f
        latestRawAngle = 0f
        onSteeringUpdated(0f, 0f, 0f)
    }

    fun calibrateCenter() {
        calibratedCenter = latestRawAngle
        manualTrimOffset = 0f
        smoothedAngle = 0f
        onSteeringUpdated(0f, 0f, 0f)
    }

    fun applyTrim(deltaDeg: Float) {
        manualTrimOffset += deltaDeg
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (!isEnabled || event == null) return

        val ax = event.values[0]
        val ay = event.values[1]
        val az = event.values[2]

        val rotation = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                context.display?.rotation ?: Surface.ROTATION_90
            } else {
                @Suppress("DEPRECATION")
                windowManager?.defaultDisplay?.rotation ?: Surface.ROTATION_90
            }
        } catch (_: Exception) {
            Surface.ROTATION_90
        }

        val lateralG: Float
        val verticalG: Float

        when (rotation) {
            Surface.ROTATION_90 -> {
                lateralG = -ay
                verticalG = hypot(ax, az)
            }
            Surface.ROTATION_270 -> {
                lateralG = ay
                verticalG = hypot(ax, az)
            }
            Surface.ROTATION_180 -> {
                lateralG = -ax
                verticalG = hypot(ay, az)
            }
            else -> {
                // Surface.ROTATION_0 (Portrait)
                lateralG = ax
                verticalG = hypot(ay, az)
            }
        }

        // Compute 3D lateral gravity arc
        val currentRollDeg = (atan2(lateralG.toDouble(), max(0.001, verticalG.toDouble())) * (180.0 / PI)).toFloat()
        latestRawAngle = currentRollDeg

        val effectiveCenter = calibratedCenter + manualTrimOffset
        var rawDelta = currentRollDeg - effectiveCenter

        if (invertSteering) {
            rawDelta = -rawDelta
        }

        // Apply OneEuro adaptive filter
        val timestampMs = event.timestamp / 1_000_000L
        val filtered = oneEuro.filter(rawDelta.toDouble(), timestampMs).toFloat()

        // High responsiveness EMA (92% filtered + 8% memory)
        smoothedAngle = (0.92f * filtered) + (0.08f * smoothedAngle)

        // Visual Cockpit Wheel Angle (2.8888x multiplier for cockpit feel)
        val visualSteerDeg = smoothedAngle * 2.8888f
        val displayAngle = if (abs(visualSteerDeg) < 0.05f) 0f else visualSteerDeg

        // Normalized Controller Output [-1.0 to +1.0]
        val safeMaxAngle = max(15, maxSteeringAngle).toFloat()
        var norm = (smoothedAngle / safeMaxAngle) * steeringSensitivity
        norm = max(-1.0f, min(1.0f, norm))

        // Phone Sensor Tremor Guard
        if (sensorDeadzone > 0f && abs(norm) < sensorDeadzone) {
            norm = 0f
        } else {
            // S-Curve Linearity
            if (curveExponent != 1.0f) {
                val s = sign(norm)
                val mag = abs(norm)
                norm = s * Math.pow(mag.toDouble(), curveExponent.toDouble()).toFloat()
            }

            // Anti-Deadzone (Game Deadband Bypass)
            if (antiDeadzone > 0f && abs(norm) > 0.0001f) {
                val s = sign(norm)
                val mag = abs(norm)
                norm = s * (antiDeadzone + (1.0f - antiDeadzone) * mag)
                norm = max(-1.0f, min(1.0f, norm))
            }
        }

        onSteeringUpdated(norm, displayAngle, smoothedAngle)
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}
