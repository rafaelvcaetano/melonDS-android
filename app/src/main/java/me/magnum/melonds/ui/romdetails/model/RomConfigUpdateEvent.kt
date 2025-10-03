package me.magnum.melonds.ui.romdetails.model

import android.net.Uri
import me.magnum.melonds.domain.model.rom.config.RuntimeConsoleType
import me.magnum.melonds.domain.model.rom.config.RuntimeMicSource
import me.magnum.melonds.domain.model.DsExternalScreen
import java.util.UUID

sealed class RomConfigUpdateEvent {
    data class RuntimeConsoleUpdate(val newRuntimeConsole: RuntimeConsoleType) : RomConfigUpdateEvent()
    data class RuntimeMicSourceUpdate(val newRuntimeMicSource: RuntimeMicSource) : RomConfigUpdateEvent()
    data class LayoutUpdate(val newLayoutId: UUID?) : RomConfigUpdateEvent()
    data class ExternalLayoutUpdate(val newLayoutId: UUID?) : RomConfigUpdateEvent()
    data class ExternalScreenUpdate(val newScreen: DsExternalScreen?) : RomConfigUpdateEvent()
    data class GbaSlotTypeUpdated(val type: RomGbaSlotConfigUiModel.Type) : RomConfigUpdateEvent()
    data class GbaRomPathUpdate(val gbaRomPath: Uri?) : RomConfigUpdateEvent()
    data class GbaSavePathUpdate(val gbaSavePath: Uri?) : RomConfigUpdateEvent()
    data class CustomNameUpdate(val customName: String?) : RomConfigUpdateEvent()
}
