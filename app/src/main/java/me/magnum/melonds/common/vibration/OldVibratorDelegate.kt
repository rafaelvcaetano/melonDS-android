package me.magnum.melonds.common.vibration

import android.os.Vibrator

@Suppress("DEPRECATION")
class OldVibratorDelegate(private val vibrator: Vibrator) : VibratorDelegate {
    override fun supportsVibration(): Boolean {
        return vibrator.hasVibrator()
    }

    override fun supportsVibrationAmplitude(): Boolean {
        return false
    }

    override fun vibrate(duration: Int, amplitude: Int) {
        vibrator.vibrate(duration.toLong())
    }

    override fun startVibrating() {
        vibrator.vibrate(longArrayOf(0, 100), 1)
    }

    override fun stopVibrating() {
        vibrator.cancel()
    }
}