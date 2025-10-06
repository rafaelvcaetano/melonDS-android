package me.magnum.melonds.common.rumble

import androidx.annotation.Keep
import kotlin.math.roundToInt

@Keep
interface GbaRumbleManager {
    fun startRumble(durationMillis: Int)
    fun stopRumble()

    companion object {
        const val MIN_INTENSITY = 1
        const val MAX_INTENSITY = 100
        private const val MAX_AMPLITUDE = 255

        fun intensityToAmplitude(intensity: Int): Int {
            val normalized = intensity.coerceIn(MIN_INTENSITY, MAX_INTENSITY) / MAX_INTENSITY.toFloat()
            return (normalized * MAX_AMPLITUDE).roundToInt().coerceIn(1, MAX_AMPLITUDE)
        }

        fun intensityToScale(intensity: Int): Float {
            return intensity.coerceIn(MIN_INTENSITY, MAX_INTENSITY) / MAX_INTENSITY.toFloat()
        }
    }
}
