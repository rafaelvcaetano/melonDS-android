package me.magnum.melonds.utils

import android.content.Context
import android.content.res.Configuration
import android.graphics.Point
import android.os.Build
import android.view.DisplayCutout
import android.view.WindowManager
import androidx.core.content.getSystemService

object WindowManagerCompat {

    fun getWindowSize(context: Context): Point {
        val windowService = context.getSystemService<WindowManager>()!!

        var cutout: DisplayCutout? = null
        val sizePoint = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val windowBounds = windowService.currentWindowMetrics.bounds
            cutout = windowService.currentWindowMetrics.windowInsets.displayCutout
            Point(windowBounds.width(), windowBounds.height())
        } else {
            val display = windowService.defaultDisplay
            val point = Point()
            display.getRealSize(point)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                cutout = display.cutout
            }
            point
        }

        var screenWidth: Int
        var screenHeight: Int
        if (context.resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT) {
            screenWidth = sizePoint.x
            screenHeight = sizePoint.y

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                cutout?.let {
                    screenWidth -= it.safeInsetLeft + it.safeInsetRight
                    screenHeight -= it.safeInsetTop + it.safeInsetBottom
                }
            }
        } else {
            screenWidth = sizePoint.y
            screenHeight = sizePoint.x

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                cutout?.let {
                    screenWidth -= it.safeInsetTop + it.safeInsetBottom
                    screenHeight -= it.safeInsetLeft + it.safeInsetRight
                }
            }
        }

        return Point(screenWidth, screenHeight)
    }
}