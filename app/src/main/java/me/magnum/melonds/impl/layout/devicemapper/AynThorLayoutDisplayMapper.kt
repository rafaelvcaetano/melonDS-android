package me.magnum.melonds.impl.layout.devicemapper

import android.content.Context
import android.view.Display
import me.magnum.melonds.domain.model.layout.LayoutDisplay
import me.magnum.melonds.domain.model.layout.LayoutDisplayPair
import me.magnum.melonds.impl.layout.DeviceLayoutDisplayMapper

/**
 * [DeviceLayoutDisplayMapper] for the AYN Thor. This device has 2 different names for the built-in screens: "Built-in Screen" for the top display and "Screen-2" for the
 * bottom display.
 */
class AynThorLayoutDisplayMapper(context: Context) : DeviceLayoutDisplayMapper(context) {

    private companion object {
        val BUILT_IN_DISPLAY_NAMES = listOf("Built-in Screen", "Screen-2")
    }

    override fun mapDisplaysToLayoutDisplays(
        currentDisplay: Display,
        secondaryDisplay: Display?,
    ): LayoutDisplayPair {
        val mainLayoutDisplay = mapDisplayToLayoutDisplay(
            display = currentDisplay,
            displayType = if (currentDisplay.name in BUILT_IN_DISPLAY_NAMES) LayoutDisplay.Type.BUILT_IN else LayoutDisplay.Type.EXTERNAL,
        )
        val secondaryLayoutDisplay = secondaryDisplay?.let {
            mapDisplayToLayoutDisplay(
                display = it,
                displayType = if (it.name in BUILT_IN_DISPLAY_NAMES) LayoutDisplay.Type.BUILT_IN else LayoutDisplay.Type.EXTERNAL,
            )
        }

        return LayoutDisplayPair(mainLayoutDisplay, secondaryLayoutDisplay)
    }
}