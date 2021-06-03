package me.magnum.melonds.common.vibration

import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.annotation.RequiresApi

@RequiresApi(Build.VERSION_CODES.O)
class Api26VibratorDelegate(private val vibrator: Vibrator) : TouchVibrator.VibratorDelegate {
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
}