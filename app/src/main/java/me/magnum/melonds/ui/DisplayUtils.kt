package me.magnum.melonds.ui

import android.app.Activity
import android.os.Build
import android.view.Display

/** Flag indicating whether the application was launched on an external display. */
var launchedFromExternalDisplay = false
    private set

/**
 * Checks the display on which the activity is running and updates
 * [launchedFromExternalDisplay] accordingly. This replaces the previous behavior
 * that forced activities onto the primary display, allowing the app to handle
 * swapped internal/external displays.
 */
fun Activity.ensureOnPrimaryDisplay() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val currentDisplay = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            display
        } else {
            @Suppress("DEPRECATION")
            windowManager.defaultDisplay
        }
        launchedFromExternalDisplay =
            currentDisplay != null && currentDisplay.displayId != Display.DEFAULT_DISPLAY
    }
}
