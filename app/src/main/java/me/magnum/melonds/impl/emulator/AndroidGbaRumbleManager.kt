package me.magnum.melonds.impl.emulator

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.annotation.Keep
import androidx.core.content.getSystemService
import me.magnum.melonds.common.rumble.GbaRumbleManager
import me.magnum.melonds.domain.repositories.SettingsRepository
import kotlin.math.roundToLong

@Keep
class AndroidGbaRumbleManager(
    context: Context,
    private val settingsRepository: SettingsRepository,
) : GbaRumbleManager {
    private val vibrator: Vibrator? = context.getSystemService()

    override fun startRumble(durationMillis: Int) {
        val deviceVibrator = vibrator ?: return
        if (!deviceVibrator.hasVibrator()) {
            return
        }

        val safeDuration = durationMillis.coerceAtLeast(1).toLong()
        val intensity = settingsRepository.getGbaRumbleIntensity()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val hasAmplitudeControl = deviceVibrator.hasAmplitudeControl()
            val duration = if (hasAmplitudeControl) {
                safeDuration
            } else {
                scaleDurationForLegacyDevices(safeDuration, intensity)
            }
            val amplitude = if (hasAmplitudeControl) {
                GbaRumbleManager.intensityToAmplitude(intensity)
            } else {
                VibrationEffect.DEFAULT_AMPLITUDE
            }
            val effect = VibrationEffect.createOneShot(duration, amplitude)
            deviceVibrator.vibrate(effect)
        } else {
            val duration = scaleDurationForLegacyDevices(safeDuration, intensity)
            @Suppress("DEPRECATION")
            deviceVibrator.vibrate(duration)
        }
    }

    override fun stopRumble() {
        vibrator?.cancel()
    }

    private fun scaleDurationForLegacyDevices(durationMillis: Long, intensity: Int): Long {
        val normalized = GbaRumbleManager.intensityToScale(intensity)
        val scaled = (durationMillis * normalized).roundToLong()
        return scaled.coerceAtLeast(1L)
    }
}
