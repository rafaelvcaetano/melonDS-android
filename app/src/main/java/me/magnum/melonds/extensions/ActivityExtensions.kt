package me.magnum.melonds.extensions

import android.app.Activity
import android.content.pm.ActivityInfo
import me.magnum.melonds.domain.model.LayoutConfiguration

/**
 * Requests the best activity orientation that satisfies the given [layoutOrientation].
 *
 * @return True if a change in [Activity.getRequestedOrientation] has been made, false otherwise.
 */
fun Activity.setLayoutOrientation(layoutOrientation: LayoutConfiguration.LayoutOrientation): Boolean {
    val desiredSystemOrientation = when(layoutOrientation) {
        LayoutConfiguration.LayoutOrientation.FOLLOW_SYSTEM -> ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        LayoutConfiguration.LayoutOrientation.PORTRAIT -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
        LayoutConfiguration.LayoutOrientation.LANDSCAPE -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
    }

    return if (requestedOrientation != desiredSystemOrientation) {
        requestedOrientation = desiredSystemOrientation
        true
    } else {
        false
    }
}