package me.magnum.melonds.ui.emulator.input

import android.content.Context
import android.os.Build
import android.os.Vibrator
import androidx.core.content.getSystemService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch
import me.magnum.melonds.common.vibration.Api26VibratorDelegate
import me.magnum.melonds.common.vibration.OldVibratorDelegate
import me.magnum.melonds.common.vibration.VibratorDelegate

class EmulatorRumbleManager(
    context: Context,
    coroutineScope: CoroutineScope,
    private val connectedControllerManager: ConnectedControllerManager,
) {

    private val vibrationMixer = RumbleEventMixer(coroutineScope)
    private var currentVibrator: VibratorDelegate? = null

    init {
        val deviceVibrator = context.getSystemService<Vibrator>()

        coroutineScope.launch {
            connectedControllerManager.managedControllers.collect { devices ->
                val vibrators = devices.mapNotNull { device ->
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        device.vibratorManager.defaultVibrator.takeIf { it.hasVibrator() }
                    } else {
                        device.vibrator.takeIf { it.hasVibrator() }
                    }
                }

                currentVibrator?.stopVibrating()
                currentVibrator = if (vibrators.isEmpty()) {
                    deviceVibrator?.let { buildSingleVibratorDelegate(it) }
                } else {
                    buildCompositeVibratorDelegate(vibrators)
                }
            }
        }

        coroutineScope.launch {
            vibrationMixer.vibratorState.collect {
                if (it) {
                    currentVibrator?.startVibrating()
                } else {
                    currentVibrator?.stopVibrating()
                }
            }
        }
    }

    fun startRumbling() {
        vibrationMixer.onRumbleStart()
    }

    fun stopRumbling() {
        vibrationMixer.onRumbleStop()
    }

    private fun buildSingleVibratorDelegate(vibrator: Vibrator): VibratorDelegate {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Api26VibratorDelegate(vibrator)
        } else {
            OldVibratorDelegate(vibrator)
        }
    }

    private fun buildCompositeVibratorDelegate(vibrators: List<Vibrator>): VibratorDelegate {
        val delegates = vibrators.map {
            buildSingleVibratorDelegate(it)
        }
        return CompositeVibratorDelegate(delegates)
    }

    /**
     * The DS Rumble Pak works weirdly. Rumble is triggered by changing a value in the pak's "memory". To create a continuous rumble effect, this value is changed every frame.
     * This means that there's no event to trigger a rumble stop. Rumbling is stopped by simply not changing the value anymore. This means that melonDS cannot accurately track
     * when the rumble starts and stops, firing stop and start events every frame the value is changed. If we were to start and stop the vibrator using these events, it would
     * do so every frame, creating an inconsistent feeling.
     *
     * The [me.magnum.melonds.ui.emulator.input.EmulatorRumbleManager.RumbleEventMixer] attempts to create a smooth rumble feeling by receiving the emulator events and
     * updating the [vibratorState] when an actual change is detected. The rumble "on" state is detected immediately, but the rumble "stop" events occur after 50ms have passed
     * without receiving any event.
     */
    @OptIn(FlowPreview::class)
    private class RumbleEventMixer(coroutineScope: CoroutineScope) {

        private val _vibratorState = MutableStateFlow(false)
        val vibratorState = _vibratorState.asStateFlow()

        private val _rumbleEvents = MutableSharedFlow<Boolean>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

        init {
            coroutineScope.launch {
                // Fire turn-on event immediately
                val turnOnFlow = _rumbleEvents.filter { it }
                // Fire turn-off event if no rumble events are received within 50ms
                val turnOffFlow = _rumbleEvents.debounce(50).map { false }

                merge(turnOnFlow, turnOffFlow).collect(_vibratorState)
            }
        }

        fun onRumbleStart() {
            _rumbleEvents.tryEmit(true)
        }

        fun onRumbleStop() {
            _rumbleEvents.tryEmit(false)
        }
    }

    private class CompositeVibratorDelegate(private val delegates: List<VibratorDelegate>) : VibratorDelegate {

        override fun supportsVibration(): Boolean {
            return delegates.any { it.supportsVibration() }
        }

        override fun supportsVibrationAmplitude(): Boolean {
            return delegates.all { it.supportsVibrationAmplitude() }
        }

        override fun vibrate(duration: Int, amplitude: Int) {
            delegates.forEach {
                it.vibrate(duration, amplitude)
            }
        }

        override fun startVibrating() {
            delegates.forEach {
                it.startVibrating()
            }
        }

        override fun stopVibrating() {
            delegates.forEach {
                it.stopVibrating()
            }
        }
    }
}