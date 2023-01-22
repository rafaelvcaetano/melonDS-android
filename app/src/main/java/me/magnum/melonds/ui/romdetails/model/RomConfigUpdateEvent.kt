package me.magnum.melonds.ui.romdetails.model

import android.net.Uri
import me.magnum.melonds.domain.model.RuntimeConsoleType
import me.magnum.melonds.domain.model.RuntimeMicSource
import java.util.UUID

sealed class RomConfigUpdateEvent {
    data class RuntimeConsoleUpdate(val newRuntimeConsole: RuntimeConsoleType) : RomConfigUpdateEvent()
    data class RuntimeMicSourceUpdate(val newRuntimeMicSource: RuntimeMicSource) : RomConfigUpdateEvent()
    data class LayoutUpdate(val newLayoutId: UUID?) : RomConfigUpdateEvent()
    data class LoadGbaRomUpdate(val shouldLoadRom: Boolean) : RomConfigUpdateEvent()
    data class GbaRomPathUpdate(val gbaRomPath: Uri?) : RomConfigUpdateEvent()
    data class GbaSavePathUpdate(val gbaSavePath: Uri?) : RomConfigUpdateEvent()
}
