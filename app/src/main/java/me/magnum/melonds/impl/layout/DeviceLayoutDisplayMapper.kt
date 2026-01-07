package me.magnum.melonds.impl.layout

import android.content.Context
import android.graphics.Point
import android.os.Build
import android.view.Display
import android.view.WindowManager
import androidx.core.content.getSystemService
import me.magnum.melonds.domain.model.layout.LayoutDisplay
import me.magnum.melonds.domain.model.layout.LayoutDisplayPair

abstract class DeviceLayoutDisplayMapper(private val context: Context) {

    abstract fun mapDisplaysToLayoutDisplays(currentDisplay: Display, secondaryDisplay: Display?): LayoutDisplayPair

    protected fun mapDisplayToLayoutDisplay(display: Display, displayType: LayoutDisplay.Type): LayoutDisplay {
        val (width, height) = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val typePresentation = 2037 // This is equal to WindowManager.LayoutParams.TYPE_PRESENTATION, but the constant can't be used for some reason
            val displayContextType = if ((display.flags and Display.FLAG_PRIVATE) != 0) WindowManager.LayoutParams.TYPE_PRIVATE_PRESENTATION else typePresentation
            val displayWindowContext = context.createDisplayContext(display).createWindowContext(displayContextType, null)
            val displayWindowManager = displayWindowContext.getSystemService<WindowManager>()!!

            val rect = displayWindowManager.currentWindowMetrics.bounds
            rect.width() to rect.height()
        } else {
            val point = Point()
            display.getRealSize(point)
            point.x to point.y
        }

        return LayoutDisplay(
            id = display.displayId,
            type = displayType,
            width = width,
            height = height,
        )
    }
}