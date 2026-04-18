package me.magnum.melonds.impl.layout.devicemapper

import android.content.Context
import android.view.Display
import me.magnum.melonds.domain.model.layout.LayoutDisplay
import me.magnum.melonds.domain.model.layout.LayoutDisplayPair
import me.magnum.melonds.impl.layout.DeviceLayoutDisplayMapper

class DefaultLayoutDisplayMapper(context: Context) : DeviceLayoutDisplayMapper(context) {

    override fun mapDisplaysToLayoutDisplays(currentDisplay: Display, secondaryDisplay: Display?): LayoutDisplayPair {
        val mainLayoutDisplay = mapDisplayToLayoutDisplay(
            display = currentDisplay,
            displayType = if (currentDisplay.name == "Built-in Screen") LayoutDisplay.Type.BUILT_IN else LayoutDisplay.Type.EXTERNAL,
        )
        val secondaryLayoutDisplay = secondaryDisplay?.let {
            mapDisplayToLayoutDisplay(
                display = it,
                displayType = if (it.name == "Built-in Screen") LayoutDisplay.Type.BUILT_IN else LayoutDisplay.Type.EXTERNAL,
            )
        }

        return LayoutDisplayPair(mainLayoutDisplay, secondaryLayoutDisplay)
    }
}