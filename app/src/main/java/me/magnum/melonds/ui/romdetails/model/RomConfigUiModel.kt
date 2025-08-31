package me.magnum.melonds.ui.romdetails.model

import me.magnum.melonds.domain.model.rom.config.RuntimeConsoleType
import me.magnum.melonds.domain.model.rom.config.RuntimeMicSource
import me.magnum.melonds.domain.model.DsExternalScreen
import java.util.UUID

data class RomConfigUiModel(
    val runtimeConsoleType: RuntimeConsoleType = RuntimeConsoleType.DEFAULT,
    val runtimeMicSource: RuntimeMicSource = RuntimeMicSource.DEFAULT,
    val layoutId: UUID? = null,
    val layoutName: String? = null,
    val externalLayoutId: UUID? = null,
    val externalLayoutName: String? = null,
    val externalScreen: DsExternalScreen? = null,
    val gbaSlotConfig: RomGbaSlotConfigUiModel = RomGbaSlotConfigUiModel()
)
