package me.magnum.melonds.ui.emulator.rom

import me.magnum.melonds.R
import me.magnum.melonds.ui.emulator.PauseMenuOption

enum class RomPauseMenuOption(override val textResource: Int) : PauseMenuOption {
    SETTINGS(R.string.settings),
    SAVE_STATE(R.string.save_state),
    LOAD_STATE(R.string.load_state),
    REWIND(R.string.rewind),
    CHEATS(R.string.cheats),
    VIEW_ACHIEVEMENTS(R.string.achievements),
    RESET(R.string.reset),
    EXIT(R.string.exit)
}