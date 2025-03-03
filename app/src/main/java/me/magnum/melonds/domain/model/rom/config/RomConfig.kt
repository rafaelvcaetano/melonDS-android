package me.magnum.melonds.domain.model.rom.config

import java.util.*

data class RomConfig(
    var runtimeConsoleType: RuntimeConsoleType = RuntimeConsoleType.DEFAULT,
    var runtimeMicSource: RuntimeMicSource = RuntimeMicSource.DEFAULT,
    var layoutId: UUID? = null,
    val gbaSlotConfig: RomGbaSlotConfig = RomGbaSlotConfig.None,
)
