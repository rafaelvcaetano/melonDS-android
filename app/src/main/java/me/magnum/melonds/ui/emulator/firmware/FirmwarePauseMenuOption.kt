package me.magnum.melonds.ui.emulator.firmware

import me.magnum.melonds.R
import me.magnum.melonds.ui.emulator.PauseMenuOption

enum class FirmwarePauseMenuOption(override val textResource: Int) : PauseMenuOption {
    SETTINGS(R.string.settings),
    RESET(R.string.reset),
    EXIT(R.string.exit)
}