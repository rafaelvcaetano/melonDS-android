package me.magnum.melonds.domain.model.rom.config

import java.util.*
import me.magnum.melonds.domain.model.DsExternalScreen

data class RomConfig(
    var runtimeConsoleType: RuntimeConsoleType = RuntimeConsoleType.DEFAULT,
    var runtimeMicSource: RuntimeMicSource = RuntimeMicSource.DEFAULT,
    var layoutId: UUID? = null,
    var externalLayoutId: UUID? = null,
    var externalScreen: DsExternalScreen? = null,
    val gbaSlotConfig: RomGbaSlotConfig = RomGbaSlotConfig.None,
)
