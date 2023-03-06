package me.magnum.melonds.ui.romdetails

import android.content.Context
import kotlinx.coroutines.rx2.awaitSingleOrNull
import me.magnum.melonds.domain.model.RomConfig
import me.magnum.melonds.domain.repositories.LayoutsRepository
import me.magnum.melonds.ui.romdetails.model.RomConfigUiModel
import me.magnum.melonds.utils.FileUtils

class RomDetailsUiMapper(
    private val context: Context,
    private val layoutsRepository: LayoutsRepository,
) {

    suspend fun mapRomConfigToUi(romConfig: RomConfig): RomConfigUiModel {
        return RomConfigUiModel(
            runtimeConsoleType = romConfig.runtimeConsoleType,
            runtimeMicSource = romConfig.runtimeMicSource,
            layoutId = romConfig.layoutId,
            layoutName = romConfig.layoutId?.let { layoutsRepository.getLayout(it).awaitSingleOrNull()?.name } ?: layoutsRepository.getGlobalLayoutPlaceholder().name,
            loadGbaCart = romConfig.loadGbaCart,
            gbaCartPath = FileUtils.getAbsolutePathFromSAFUri(context, romConfig.gbaCartPath),
            gbaSavePath = FileUtils.getAbsolutePathFromSAFUri(context, romConfig.gbaSavePath),
        )
    }
}
