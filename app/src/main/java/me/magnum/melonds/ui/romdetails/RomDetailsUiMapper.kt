package me.magnum.melonds.ui.romdetails

import android.content.Context
import androidx.documentfile.provider.DocumentFile
import me.magnum.melonds.domain.model.rom.config.RomConfig
import me.magnum.melonds.domain.model.rom.config.RomGbaSlotConfig
import me.magnum.melonds.domain.repositories.LayoutsRepository
import me.magnum.melonds.ui.romdetails.model.RomConfigUiModel
import me.magnum.melonds.ui.romdetails.model.RomGbaSlotConfigUiModel

class RomDetailsUiMapper(
    private val context: Context,
    private val layoutsRepository: LayoutsRepository,
) {

    suspend fun mapRomConfigToUi(romConfig: RomConfig): RomConfigUiModel {
        return RomConfigUiModel(
            runtimeConsoleType = romConfig.runtimeConsoleType,
            runtimeMicSource = romConfig.runtimeMicSource,
            layoutId = romConfig.layoutId,
            layoutName = romConfig.layoutId?.let { layoutsRepository.getLayout(it)?.name } ?: layoutsRepository.getGlobalLayoutPlaceholder().name,
            gbaSlotConfig = mapGbaSlotConfigToUi(romConfig.gbaSlotConfig),
            customName = romConfig.customName,
        )
    }

    private fun mapGbaSlotConfigToUi(gbaSlotConfig: RomGbaSlotConfig): RomGbaSlotConfigUiModel {
        return when (gbaSlotConfig) {
            is RomGbaSlotConfig.None -> RomGbaSlotConfigUiModel(type = RomGbaSlotConfigUiModel.Type.None)
            is RomGbaSlotConfig.GbaRom -> RomGbaSlotConfigUiModel(
                type = RomGbaSlotConfigUiModel.Type.GbaRom,
                gbaRomPath = gbaSlotConfig.romPath?.let { DocumentFile.fromSingleUri(context, it)?.name },
                gbaSavePath = gbaSlotConfig.savePath?.let { DocumentFile.fromSingleUri(context, it)?.name },
            )
            is RomGbaSlotConfig.MemoryExpansion -> RomGbaSlotConfigUiModel(type = RomGbaSlotConfigUiModel.Type.MemoryExpansion)
            is RomGbaSlotConfig.RumblePak -> RomGbaSlotConfigUiModel(type = RomGbaSlotConfigUiModel.Type.RumblePak)
        }
    }
}
