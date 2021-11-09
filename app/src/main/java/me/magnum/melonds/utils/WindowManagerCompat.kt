package me.magnum.melonds.utils

import android.content.Context
import android.content.res.Configuration
import android.graphics.Point
import android.os.Build
import android.view.WindowManager
import androidx.core.content.getSystemService

object WindowManagerCompat {

    fun getWindowSize(context: Context): Point {
        val windowService = context.getSystemService<WindowManager>()!!
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val windowBounds = windowService.currentWindowMetrics.bounds
            if (context.resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT) {
                Point(windowBounds.width(), windowBounds.height())
            } else {
                Point(windowBounds.height(), windowBounds.width())
            }
        } else {
            val display = windowService.defaultDisplay
            val point = Point()
            display.getRealSize(point)

            var screenWidth: Int
            var screenHeight: Int
            if (context.resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT) {
                screenWidth = point.x
                screenHeight = point.y

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    display.cutout?.let {
                        screenWidth -= it.safeInsetLeft + it.safeInsetRight
                        screenHeight -= it.safeInsetTop + it.safeInsetBottom
                    }
                }
            } else {
                screenWidth = point.y
                screenHeight = point.x

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    display.cutout?.let {
                        screenWidth -= it.safeInsetTop + it.safeInsetBottom
                        screenHeight -= it.safeInsetLeft + it.safeInsetRight
                    }
                }
            }

            Point(screenWidth, screenHeight)
        }
    }
}