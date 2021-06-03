package me.magnum.melonds.common.vibration

import me.magnum.melonds.domain.repositories.SettingsRepository

class TouchVibrator(private val delegate: VibratorDelegate, private val settingsRepository: SettingsRepository) {
    interface VibratorDelegate {
        fun supportsVibration(): Boolean
        fun supportsVibrationAmplitude(): Boolean
        fun vibrate(duration: Int, amplitude: Int)
    }

    companion object {
        private const val VIBRATION_DURATION = 100
    }

    fun supportsVibration() = delegate.supportsVibration()

    /**
     * Vibrates the device to provide touch feedback with the strength provided by the [SettingsRepository].
     */
    fun performTouchHapticFeedback() {
        val vibrationStrength = settingsRepository.getTouchHapticFeedbackStrength()
        performTouchHapticFeedback(vibrationStrength)
    }

    /**
     * Vibrates the device to provide touch feedback.
     *
     * @param vibrationStrength The strength of the vibration (between 1 and 255)
     */
    fun performTouchHapticFeedback(vibrationStrength: Int) {
        val duration = if (delegate.supportsVibrationAmplitude()) {
            VIBRATION_DURATION
        } else {
            // If variable amplitude is not supported, adjust the duration to create a similar effect
            val adjustedAmplitude = (vibrationStrength / 100f) * 2f
            (VIBRATION_DURATION * adjustedAmplitude).toInt()
        }
        delegate.vibrate(duration, vibrationStrength.coerceIn(1, 255))
    }
}