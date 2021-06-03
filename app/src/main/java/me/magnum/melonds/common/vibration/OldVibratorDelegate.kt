package me.magnum.melonds.common.vibration

import android.os.Vibrator

@Suppress("DEPRECATION")
class OldVibratorDelegate(private val vibrator: Vibrator) : TouchVibrator.VibratorDelegate {
    override fun supportsVibration(): Boolean {
        return vibrator.hasVibrator()
    }

    override fun supportsVibrationAmplitude(): Boolean {
        return false
    }

    override fun vibrate(duration: Int, amplitude: Int) {
        vibrator.vibrate(duration.toLong())
    }
}