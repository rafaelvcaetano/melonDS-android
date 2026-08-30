package me.magnum.melonds.ui.emulator.input

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.view.Display
import android.view.InputDevice
import android.view.Surface
import androidx.collection.mutableIntListOf
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import me.magnum.melonds.MelonEmulator

private const val GRAVITY = 9.80665f

class EmulatorMotionManager(
    private val context: Context,
    private val coroutineScope: CoroutineScope,
    private val connectedControllerManager: ConnectedControllerManager,
) {

    private val display: Display = ContextCompat.getDisplayOrDefault(context)

    private var isPaused = false
    private var sensorJob: Job? = null
    private var currentSensorManager: SensorManager? = null

    private var accelX = 0f
    private var accelY = 0f
    private var accelZ = GRAVITY
    private var gyroX = 0f
    private var gyroY = 0f
    private var gyroZ = 0f

    private val deviceSensorListener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            val rawX = event.values[0]
            val rawY = event.values[1]
            val rawZ = event.values[2]

            // Remap X and Y axes based on display rotation so that sensor output always corresponds to the current screen orientation
            val (remappedX, remappedY) = remapForDisplayRotation(rawX, rawY)

            when (event.sensor.type) {
                Sensor.TYPE_ACCELEROMETER -> {
                    accelX = remappedX
                    accelY = remappedY
                    accelZ = rawZ
                }
                Sensor.TYPE_GYROSCOPE -> {
                    gyroX = remappedX
                    gyroY = remappedY
                    gyroZ = rawZ
                }
            }
            pushMotionData()
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) { }
    }

    private val controllerSensorListener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            // DS X = Controller X
            // DS Y = -Controller Z
            // DS Z = Controller Y
            when (event.sensor.type) {
                Sensor.TYPE_ACCELEROMETER -> {
                    accelX = event.values[0]
                    accelY = -event.values[2]
                    accelZ = event.values[1]
                }
                Sensor.TYPE_GYROSCOPE -> {
                    gyroX = event.values[0]
                    gyroY = -event.values[2]
                    gyroZ = event.values[1]
                }
            }
            pushMotionData()
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) { }
    }

    fun start() {
        if (sensorJob != null) {
            return
        }

        isPaused = false
        resetMotionValues()
        sensorJob = coroutineScope.launch {
            connectedControllerManager.managedControllers.collect { devices ->
                switchSensorSource(devices)
            }
        }
    }

    fun resume() {
        if (!isPaused) {
            return
        }

        start()
        isPaused = false
    }

    fun pause() {
        if (isPaused || sensorJob == null) {
            return
        }

        stop()
        isPaused = true
    }

    fun stop() {
        isPaused = false
        if (sensorJob == null) {
            return
        }

        sensorJob?.cancel()
        sensorJob = null
        unregisterCurrentSensors()
    }

    private fun switchSensorSource(controllers: List<InputDevice>) {
        unregisterCurrentSensors()

        // Try to use controller sensors first
        val controllerSensorManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            controllers.firstNotNullOfOrNull { device ->
                device.sensorManager.takeIf {
                    it.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) != null
                }
            }
        } else {
            null
        }

        if (controllerSensorManager != null && registerSensors(controllerSensorManager, controllerSensorListener).isNotEmpty()) {
            return
        }

        // Fallback to device sensors if no controller that supports motion is found
        val deviceSensorManager = context.getSystemService<SensorManager>()
        if (deviceSensorManager != null && registerSensors(deviceSensorManager, deviceSensorListener).isNotEmpty()) {
            return
        }

        // Device doesn't support motion. Set sane default values
        resetMotionValues()
        pushMotionData()
    }

    private fun registerSensors(sensorManager: SensorManager, listener: SensorEventListener): List<Int> {
        currentSensorManager = sensorManager

        val enabledSensors = mutableListOf<Int>()
        sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)?.let {
            sensorManager.registerListener(listener, it, SensorManager.SENSOR_DELAY_GAME)
            enabledSensors.add(Sensor.TYPE_ACCELEROMETER)
        }

        sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)?.let {
            sensorManager.registerListener(listener, it, SensorManager.SENSOR_DELAY_GAME)
            enabledSensors.add(Sensor.TYPE_GYROSCOPE)
        }

        return enabledSensors
    }

    private fun unregisterCurrentSensors() {
        currentSensorManager?.apply {
            unregisterListener(deviceSensorListener)
            unregisterListener(controllerSensorListener)
        }
        currentSensorManager = null
    }

    /**
     * Remaps sensor X/Y values to account for the current display rotation. Android sensor axes are fixed to the device's natural orientation. When the screen is rotated, we
     * need to rotate the sensor values to match.
     */
    private fun remapForDisplayRotation(x: Float, y: Float): Pair<Float, Float> {
        return when (display.rotation) {
            Surface.ROTATION_90 -> Pair(-y, x)
            Surface.ROTATION_180 -> Pair(-x, -y)
            Surface.ROTATION_270 -> Pair(y, -x)
            else -> Pair(x, y) // ROTATION_0
        }
    }

    private fun resetMotionValues() {
        accelX = 0f
        accelY = 0f
        accelZ = GRAVITY
        gyroX = 0f
        gyroY = 0f
        gyroZ = 0f
    }

    private fun pushMotionData() {
        MelonEmulator.updateMotionData(accelX, accelY, accelZ, gyroX, gyroY, gyroZ)
    }
}