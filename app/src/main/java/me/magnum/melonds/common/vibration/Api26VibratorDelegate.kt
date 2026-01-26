package me.magnum.melonds.common.vibration

import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.annotation.RequiresApi

@RequiresApi(Build.VERSION_CODES.O)
class Api26VibratorDelegate(private val vibrator: Vibrator) : VibratorDelegate {
    override fun supportsVibration(): Boolean {
        return vibrator.hasVibrator()
    }

    override fun supportsVibrationAmplitude(): Boolean {
        return vibrator.hasAmplitudeControl()
    }

    override fun vibrate(duration: Int, amplitude: Int) {
        val effect = VibrationEffect.createOneShot(duration.toLong(), amplitude)
        vibrator.vibrate(effect)
    }

    override fun startVibrating() {
        val vibrationPattern = longArrayOf(0, 100)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA) {
            val effect = VibrationEffect.createRepeatingEffect(VibrationEffect.createWaveform(vibrationPattern, -1))
            vibrator.vibrate(effect)
        } else {
            vibrator.vibrate(vibrationPattern, 1)
        }
    }

    override fun stopVibrating() {
        vibrator.cancel()
    }
}