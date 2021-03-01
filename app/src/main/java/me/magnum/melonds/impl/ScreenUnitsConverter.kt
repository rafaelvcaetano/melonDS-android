package me.magnum.melonds.impl

import android.content.Context
import android.util.DisplayMetrics

class ScreenUnitsConverter(private val context: Context) {
    fun pixelsToDp(pixels: Float): Float {
        return pixels / (context.resources.displayMetrics.densityDpi.toFloat() / DisplayMetrics.DENSITY_DEFAULT)
    }

    fun dpToPixels(dp: Float): Float {
        return dp * (context.resources.displayMetrics.densityDpi.toFloat() / DisplayMetrics.DENSITY_DEFAULT)
    }
}