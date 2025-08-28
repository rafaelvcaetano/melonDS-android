package me.magnum.melonds.ui

import android.app.Activity
import android.app.ActivityOptions
import android.content.Intent
import android.hardware.display.DisplayManager
import android.os.Build
import android.view.Display
import androidx.core.content.getSystemService

/**
 * Ensures that the activity runs on the primary (internal) display. If the activity is currently
 * displayed on an external screen, it relaunches itself on the default display and finishes the
 * current instance.
 */
fun Activity.ensureOnPrimaryDisplay() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val currentDisplay = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            display
        } else {
            @Suppress("DEPRECATION")
            windowManager.defaultDisplay
        }
        if (currentDisplay != null && currentDisplay.displayId != Display.DEFAULT_DISPLAY) {
            val dm = getSystemService<DisplayManager>() ?: return
            val mainDisplay = dm.getDisplay(Display.DEFAULT_DISPLAY) ?: return

            // Relaunch the same intent on the primary display
            val newIntent = Intent(intent)
            val options = ActivityOptions.makeBasic()
            options.launchDisplayId = mainDisplay.displayId
            startActivity(newIntent, options.toBundle())
            finish()
        }
    }
}
