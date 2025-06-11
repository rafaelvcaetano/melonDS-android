package me.magnum.melonds.ui

import android.content.Context
import android.graphics.Color
import android.hardware.display.DisplayManager
import android.os.Bundle
import android.view.Display
import me.magnum.melonds.ui.romlist.RomListActivity

/**
 * Entry activity that also manages an external display if available.
 */
@dagger.hilt.android.AndroidEntryPoint
class MainActivity : RomListActivity() {
    private var presentation: ExternalPresentation? = null
    private lateinit var displayManager: DisplayManager
    @javax.inject.Inject lateinit var settingsRepository: me.magnum.melonds.domain.repositories.SettingsRepository
    private val displayListener = object : DisplayManager.DisplayListener {
        override fun onDisplayAdded(displayId: Int) {
            showExternalDisplay()
        }

        override fun onDisplayRemoved(displayId: Int) {
            if (presentation?.display?.displayId == displayId) {
                presentation?.dismiss()
                presentation = null
                ExternalDisplayManager.presentation = null
            }
        }

        override fun onDisplayChanged(displayId: Int) {
            // No-op
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        displayManager = getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
        displayManager.registerDisplayListener(displayListener, null)
        showExternalDisplay()
    }

    override fun onDestroy() {
        super.onDestroy()
        displayManager.unregisterDisplayListener(displayListener)
        presentation?.dismiss()
        ExternalDisplayManager.presentation = null
    }

    private fun showExternalDisplay() {
        if (presentation != null) return
        val external = displayManager.displays.firstOrNull { it.displayId != Display.DEFAULT_DISPLAY }
        if (external != null) {
            presentation = ExternalPresentation(this, external).apply {
                setBackground(Color.parseColor("#8B0000"))
                setOrientation(settingsRepository.getExternalDisplayOrientation())
                show()
            }
            ExternalDisplayManager.presentation = presentation
        }
    }

    override fun loadRom(rom: me.magnum.melonds.domain.model.rom.Rom) {
        super.loadRom(rom)
        showExternalDisplay()
        presentation?.apply {
            setBackground(Color.parseColor("#00008B"))
            setOrientation(settingsRepository.getExternalDisplayOrientation())
        }
    }
}
