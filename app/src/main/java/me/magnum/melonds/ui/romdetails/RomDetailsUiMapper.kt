package me.magnum.melonds.ui.romdetails

import android.content.Context
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.flow.first
import me.magnum.melonds.domain.model.rom.config.RomConfig
import me.magnum.melonds.domain.model.rom.config.RomGbaSlotConfig
import me.magnum.melonds.domain.model.RomInfo
import me.magnum.melonds.domain.repositories.CheatsRepository
import me.magnum.melonds.domain.repositories.LayoutsRepository
import me.magnum.melonds.ui.romdetails.model.RomCheatSummary
import me.magnum.melonds.ui.romdetails.model.RomConfigUiModel
import me.magnum.melonds.ui.romdetails.model.RomGbaSlotConfigUiModel

class RomDetailsUiMapper(
    private val context: Context,
    private val layoutsRepository: LayoutsRepository,
    private val cheatsRepository: CheatsRepository,
) {

    suspend fun mapRomConfigToUi(
        romConfig: RomConfig,
        romInfo: RomInfo?,
    ): RomConfigUiModel {
        return RomConfigUiModel(
            runtimeConsoleType = romConfig.runtimeConsoleType,
            runtimeMicSource = romConfig.runtimeMicSource,
            layoutId = romConfig.layoutId,
            layoutName = romConfig.layoutId?.let { layoutsRepository.getLayout(it)?.name } ?: layoutsRepository.getGlobalLayoutPlaceholder().name,
            gbaSlotConfig = mapGbaSlotConfigToUi(romConfig.gbaSlotConfig),
            customName = romConfig.customName,
            cheatSummary = getRomCheatSummary(romInfo),
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
            RomGbaSlotConfig.RumblePak -> RomGbaSlotConfigUiModel(type = RomGbaSlotConfigUiModel.Type.RumblePak)
            is RomGbaSlotConfig.MemoryExpansion -> RomGbaSlotConfigUiModel(type = RomGbaSlotConfigUiModel.Type.MemoryExpansion)
            RomGbaSlotConfig.MotionPakHomebrew -> RomGbaSlotConfigUiModel(type = RomGbaSlotConfigUiModel.Type.MotionPakHomebrew)
            RomGbaSlotConfig.MotionPakRetail -> RomGbaSlotConfigUiModel(type = RomGbaSlotConfigUiModel.Type.MotionPakRetail)
        }
    }

    private suspend fun getRomCheatSummary(romInfo: RomInfo?): RomCheatSummary {
        val game = romInfo?.let { cheatsRepository.findGameForRom(it) }
            ?: return RomCheatSummary(total = 0, enabled = 0)

        val cheats = cheatsRepository.getAllGameCheats(game).first().flatMap { it.cheats }
        return RomCheatSummary(total = cheats.size, enabled = cheats.count { it.enabled })
    }
}
