package me.magnum.melonds.impl

import android.graphics.Bitmap
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import me.magnum.melonds.common.uridelegates.UriHandler
import me.magnum.melonds.domain.model.rom.Rom
import me.magnum.melonds.domain.model.SaveStateSlot
import me.magnum.melonds.domain.repositories.SaveStatesRepository
import me.magnum.melonds.domain.repositories.SettingsRepository
import me.magnum.melonds.ui.emulator.exceptions.SaveSlotLoadException
import java.util.*

class FileSystemSaveStatesRepository(
    private val settingsRepository: SettingsRepository,
    private val saveStateScreenshotProvider: SaveStateScreenshotProvider,
    private val uriHandler: UriHandler
) : SaveStatesRepository {

    override fun getRomSaveStates(rom: Rom): List<SaveStateSlot> {
        val saveStateDirectoryDocument = getSaveStateDirectoryDocument(rom) ?: return emptyList()
        val storageStems = getRomStorageStems(rom) ?: return emptyList()

        val saveStateSlots = Array(9) {
            SaveStateSlot(it, false, null, null)
        }
        for (slotNumber in saveStateSlots.indices) {
            val document = findSaveState(saveStateDirectoryDocument, storageStems, slotNumber) ?: continue
            val slot = SaveStateSlot(slotNumber, true, Date(document.lastModified()), null)
            val screenshotUri = saveStateScreenshotProvider.getRomSaveStateScreenshotUri(rom, slot)
            saveStateSlots[slotNumber] = slot.copy(screenshot = screenshotUri)
        }

        return saveStateSlots.toList()
    }

    override fun getRomQuickSaveStateSlot(rom: Rom): SaveStateSlot {
        val quickSaveStateDocument = getRomQuickSaveStateDocument(rom)
        val saveStateExists = quickSaveStateDocument != null
        val lastModified = quickSaveStateDocument?.let { Date(it.lastModified()) }
        val slot = SaveStateSlot(SaveStateSlot.QUICK_SAVE_SLOT, saveStateExists, lastModified, null)
        val screenshotUri = saveStateScreenshotProvider.getRomSaveStateScreenshotUri(rom, slot)
        return slot.copy(screenshot = screenshotUri)
    }

    override fun getRomSaveStateUri(rom: Rom, saveState: SaveStateSlot): Uri {
        val saveStateDirectoryDocument = getSaveStateDirectoryDocument(rom) ?: throw SaveSlotLoadException("Could not create parent directory document")

        val storageStems = getRomStorageStems(rom) ?: throw SaveSlotLoadException("Could not determine ROM file name")
        val saveStateName = "${storageStems.first()}.ml${saveState.slot}"
        val saveStateFile = findSaveState(saveStateDirectoryDocument, storageStems, saveState.slot)

        val uri = if (saveStateFile != null) {
            saveStateFile.uri
        } else {
            saveStateDirectoryDocument.createFile("*/*", saveStateName)?.uri ?: throw SaveSlotLoadException("Could not create save state file")
        }

        return uri
    }

    override fun setRomSaveStateScreenshot(rom: Rom, saveState: SaveStateSlot, screenshot: Bitmap) {
        saveStateScreenshotProvider.saveRomSaveStateScreenshot(rom, saveState, screenshot)
    }

    override fun deleteRomSaveStateScreenshot(rom: Rom, saveState: SaveStateSlot) {
        saveStateScreenshotProvider.deleteRomSaveStateScreenshot(rom, saveState)
    }

    override fun deleteRomSaveState(rom: Rom, saveState: SaveStateSlot) {
        if (!saveState.exists) {
            return
        }

        val saveStateDirectoryDocument = getSaveStateDirectoryDocument(rom) ?: throw SaveSlotLoadException("Could not create parent directory document")
        val storageStems = getRomStorageStems(rom) ?: throw SaveSlotLoadException("Could not determine ROM file name")
        val saveStateFile = findSaveState(saveStateDirectoryDocument, storageStems, saveState.slot)

        saveStateFile?.delete()
        saveStateScreenshotProvider.deleteRomSaveStateScreenshot(rom, saveState)
    }

    private fun getRomQuickSaveStateDocument(rom: Rom): DocumentFile? {
        val saveStateDirectoryDocument = getSaveStateDirectoryDocument(rom) ?: return null
        val storageStems = getRomStorageStems(rom) ?: return null
        return findSaveState(saveStateDirectoryDocument, storageStems, 0)
    }

    private fun getSaveStateDirectoryDocument(rom: Rom): DocumentFile? {
        val saveStateDirectoryUri = settingsRepository.getSaveStateDirectory(rom) ?: return null
        return uriHandler.getUriTreeDocument(saveStateDirectoryUri)
    }

    private fun getRomStorageStems(rom: Rom): List<String>? {
        val romDocument = uriHandler.getUriDocument(rom.uri)
        return romDocument?.name?.let {
            RomStorageName.compatibleStems(
                it,
                rom.uri.toString(),
                rom.parentTreeUri == null,
                allowLegacyFallback = rom.storageNameVersion == 0 && it == rom.fileName,
            )
        }
    }

    private fun findSaveState(directory: DocumentFile, stems: List<String>, slot: Int): DocumentFile? =
        stems.firstNotNullOfOrNull { directory.findFile("$it.ml$slot") }
}
