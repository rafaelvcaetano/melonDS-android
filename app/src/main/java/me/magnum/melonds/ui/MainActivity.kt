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
class MainActivity : RomListActivity() {
    private var presentation: ExternalPresentation? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        showExternalDisplay()
    }

    override fun onDestroy() {
        super.onDestroy()
        presentation?.dismiss()
        ExternalDisplayManager.presentation = null
    }

    private fun showExternalDisplay() {
        val dm = getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
        val external = dm.displays.firstOrNull { it.displayId != Display.DEFAULT_DISPLAY }
        if (external != null) {
            presentation = ExternalPresentation(this, external).apply {
                setBackground(Color.parseColor("#8B0000"))
                show()
            }
            ExternalDisplayManager.presentation = presentation
        }
    }

    override fun loadRom(rom: me.magnum.melonds.domain.model.rom.Rom) {
        super.loadRom(rom)
        presentation?.setBackground(Color.parseColor("#00008B"))
    }
}
