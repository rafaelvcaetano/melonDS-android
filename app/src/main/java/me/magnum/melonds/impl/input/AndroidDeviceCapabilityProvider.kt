package me.magnum.melonds.impl.input

import android.content.Context
import android.hardware.display.DisplayManager
import androidx.core.content.getSystemService
import dagger.hilt.android.qualifiers.ApplicationContext
import me.magnum.melonds.domain.model.layout.DeviceCapability
import me.magnum.melonds.domain.services.DeviceCapabilityProvider
import javax.inject.Inject

class AndroidDeviceCapabilityProvider @Inject constructor(@ApplicationContext private val context: Context) : DeviceCapabilityProvider {

    private companion object {
        const val BUILT_IN_SCREEN_NAME = "Built-in Screen"
    }

    override fun getDeviceCapabilities(): List<DeviceCapability> {
        return buildList {
            if (hasDualScreenSupport()) {
                add(DeviceCapability.DUAL_SCREEN)
            }
        }
    }

    private fun hasDualScreenSupport(): Boolean {
        val displayManager = context.getSystemService<DisplayManager>() ?: return false
        val presentationDisplays = displayManager.getDisplays(DisplayManager.DISPLAY_CATEGORY_PRESENTATION)
        return presentationDisplays.count { it.name == BUILT_IN_SCREEN_NAME } >= 2
    }
}