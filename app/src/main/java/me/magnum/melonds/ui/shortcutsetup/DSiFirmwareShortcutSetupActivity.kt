package me.magnum.melonds.ui.shortcutsetup

import me.magnum.melonds.domain.model.ConsoleType

class DSiFirmwareShortcutSetupActivity : FirmwareShortcutSetupActivity() {
    override fun getConsoleType(): ConsoleType {
        return ConsoleType.DSi
    }
}