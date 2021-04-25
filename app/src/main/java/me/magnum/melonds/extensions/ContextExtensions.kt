package me.magnum.melonds.extensions

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import androidx.core.content.ContextCompat

fun Context.isMicrophonePermissionGranted(): Boolean {
    return ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
}

fun Context.isSustainedPerformanceModeAvailable(): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
        val powerManager = this.getSystemService(Context.POWER_SERVICE) as PowerManager
        powerManager.isSustainedPerformanceModeSupported
    } else {
        false
    }
}