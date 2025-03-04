package me.magnum.melonds.domain.model.rom.config

import android.net.Uri

sealed class RomGbaSlotConfig {
    data object None : RomGbaSlotConfig()
    data class GbaRom(val romPath: Uri?, val savePath: Uri?) : RomGbaSlotConfig()
    data object MemoryExpansion : RomGbaSlotConfig()
}
