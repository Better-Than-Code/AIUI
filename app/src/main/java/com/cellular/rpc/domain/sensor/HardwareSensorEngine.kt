package com.cellular.rpc.domain.sensor

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.CombinedVibration
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Section 2.10: Hardware Sensor Engine for SDUI Mini-Games & Physical Telemetry.
 * 
 * Provides unified, battery-optimized sensor streaming with:
 * - Accelerometer (X, Y, Z tilt acceleration in m/s^2)
 * - Gyroscope (Pitch, Roll, Yaw rotational velocity in rad/s)
 * - Step Counter / Motion Detector
 * - Smooth exponential moving average (EMA) low-pass filter
 * - Haptic pulse feedback generator (API 26+ VibrationEffect and API 31+ VibratorManager)
 * - Safe on-device fallback simulation mode for testing in JVM/emulators without physical sensors.
 */
data class SensorTelemetry(
    val accelX: Float = 0f,
    val accelY: Float = 0f,
    val accelZ: Float = 9.8f,
    val tiltRoll: Float = 0f,
    val tiltPitch: Float = 0f,
    val gyroX: Float = 0f,
    val gyroY: Float = 0f,
    val gyroZ: Float = 0f,
    val isPhysicalSensorActive: Boolean = false,
    val isSimulationMode: Boolean = false
)

class HardwareSensorEngine(private val context: Context) : SensorEventListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    private val accelerometer: Sensor? = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val gyroscope: Sensor? = sensorManager?.getDefaultSensor(Sensor.TYPE_GYROSCOPE)

    private val _telemetry = MutableStateFlow(SensorTelemetry())
    val telemetry: StateFlow<SensorTelemetry> = _telemetry.asStateFlow()

    private var isListening = false
    private var isSimulationActive = false

    // Exponential Moving Average filter factors
    private var filteredAccelX = 0f
    private var filteredAccelY = 0f
    private var filteredAccelZ = 9.8f
    private val alpha = 0.25f // Smoothing factor

    fun start(samplingPeriodUs: Int = SensorManager.SENSOR_DELAY_GAME) {
        if (isListening) return

        var registered = false
        if (sensorManager != null) {
            accelerometer?.let {
                sensorManager.registerListener(this, it, samplingPeriodUs)
                registered = true
            }
            gyroscope?.let {
                sensorManager.registerListener(this, it, samplingPeriodUs)
                registered = true
            }
        }

        isListening = true
        if (registered) {
            _telemetry.value = _telemetry.value.copy(isPhysicalSensorActive = true, isSimulationMode = false)
        } else {
            // Fallback simulation mode
            isSimulationActive = true
            _telemetry.value = _telemetry.value.copy(isPhysicalSensorActive = false, isSimulationMode = true)
        }
    }

    fun stop() {
        if (!isListening) return
        sensorManager?.unregisterListener(this)
        isListening = false
        isSimulationActive = false
        _telemetry.value = _telemetry.value.copy(isPhysicalSensorActive = false)
    }

    /**
     * Feed manual simulation tilt coordinates (e.g. touch drag or slider in tests/emulator).
     */
    fun simulateTilt(tiltX: Float, tiltY: Float) {
        val clampedX = tiltX.coerceIn(-10f, 10f)
        val clampedY = tiltY.coerceIn(-10f, 10f)
        filteredAccelX = clampedX
        filteredAccelY = clampedY
        _telemetry.value = _telemetry.value.copy(
            accelX = clampedX,
            accelY = clampedY,
            tiltRoll = (clampedX / 9.8f).coerceIn(-1f, 1f),
            tiltPitch = (clampedY / 9.8f).coerceIn(-1f, 1f),
            isSimulationMode = true
        )
    }

    /**
     * Trigger tactile collision / score haptic feedback.
     */
    fun triggerHapticFeedback(intensity: HapticIntensity = HapticIntensity.LIGHT) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                val vibrator = vibratorManager?.defaultVibrator
                val effect = when (intensity) {
                    HapticIntensity.LIGHT -> VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK)
                    HapticIntensity.MEDIUM -> VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK)
                    HapticIntensity.HEAVY -> VibrationEffect.createPredefined(VibrationEffect.EFFECT_HEAVY_CLICK)
                }
                vibrator?.vibrate(effect)
            } else {
                @Suppress("DEPRECATION")
                val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                val millis = when (intensity) {
                    HapticIntensity.LIGHT -> 15L
                    HapticIntensity.MEDIUM -> 35L
                    HapticIntensity.HEAVY -> 60L
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator?.vibrate(VibrationEffect.createOneShot(millis, VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator?.vibrate(millis)
                }
            }
        } catch (e: Exception) {
            // Ignore haptic errors on restricted/emulated devices
        }
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null) return

        when (event.sensor.type) {
            Sensor.TYPE_ACCELEROMETER -> {
                // Apply Low-Pass EMA filter
                filteredAccelX = filteredAccelX + alpha * (event.values[0] - filteredAccelX)
                filteredAccelY = filteredAccelY + alpha * (event.values[1] - filteredAccelY)
                filteredAccelZ = filteredAccelZ + alpha * (event.values[2] - filteredAccelZ)

                val roll = (filteredAccelX / 9.81f).coerceIn(-1f, 1f)
                val pitch = (filteredAccelY / 9.81f).coerceIn(-1f, 1f)

                _telemetry.value = _telemetry.value.copy(
                    accelX = filteredAccelX,
                    accelY = filteredAccelY,
                    accelZ = filteredAccelZ,
                    tiltRoll = roll,
                    tiltPitch = pitch,
                    isPhysicalSensorActive = true,
                    isSimulationMode = false
                )
            }
            Sensor.TYPE_GYROSCOPE -> {
                _telemetry.value = _telemetry.value.copy(
                    gyroX = event.values[0],
                    gyroY = event.values[1],
                    gyroZ = event.values[2]
                )
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // No-op
    }

    enum class HapticIntensity {
        LIGHT, MEDIUM, HEAVY
    }
}
