package me.magnum.melonds.impl

import android.graphics.Bitmap
import android.net.Uri
import me.magnum.melonds.common.uridelegates.UriHandler
import me.magnum.melonds.domain.model.Rom
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
        val saveStateDirectoryUri = settingsRepository.getSaveStateDirectory(rom) ?: return emptyList()
        val saveStateDirectoryDocument = uriHandler.getUriTreeDocument(saveStateDirectoryUri) ?: return emptyList()
        val romDocument = uriHandler.getUriDocument(rom.uri)!!
        val romFileName = romDocument.name?.substringBeforeLast('.') ?: return emptyList()

        val saveStateSlots = Array(8) {
            SaveStateSlot(it + 1, false, null, null)
        }
        val fileNameRegex = "${Regex.escape(romFileName)}\\.ml[1-8]".toRegex()
        saveStateDirectoryDocument.listFiles().forEach {
            val fileName = it.name
            if (fileName?.matches(fileNameRegex) == true) {
                val slotNumber = fileName.last().digitToInt()
                val slot = SaveStateSlot(slotNumber, true, Date(it.lastModified()), null)
                val screenshotUri = saveStateScreenshotProvider.getRomSaveStateScreenshotUri(rom, slot)
                saveStateSlots[slotNumber - 1] = slot.copy(screenshot = screenshotUri)
            }
        }

        return saveStateSlots.toList()
    }

    override fun getRomSaveStateUri(rom: Rom, saveState: SaveStateSlot): Uri {
        val saveStateDirectoryUri = settingsRepository.getSaveStateDirectory(rom) ?: throw SaveSlotLoadException("Could not determine save slot parent directory")
        val saveStateDirectoryDocument = uriHandler.getUriTreeDocument(saveStateDirectoryUri) ?: throw SaveSlotLoadException("Could not create parent directory document")

        val romDocument = uriHandler.getUriDocument(rom.uri) ?: throw SaveSlotLoadException("Could not create ROM document")
        val romFileName = romDocument.name?.substringBeforeLast('.') ?: throw SaveSlotLoadException("Could not determine ROM file name")
        val saveStateName = "$romFileName.ml${saveState.slot}"
        val saveStateFile = saveStateDirectoryDocument.findFile(saveStateName)

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

    override fun deleteRomSaveState(rom: Rom, saveState: SaveStateSlot) {
        if (!saveState.exists) {
            return
        }

        val saveStateDirectoryUri = settingsRepository.getSaveStateDirectory(rom) ?: throw SaveSlotLoadException("Could not determine save slot parent directory")
        val saveStateDirectoryDocument = uriHandler.getUriTreeDocument(saveStateDirectoryUri) ?: throw SaveSlotLoadException("Could not create parent directory document")

        val romDocument = uriHandler.getUriDocument(rom.uri) ?: throw SaveSlotLoadException("Could not create ROM document")
        val romFileName = romDocument.name?.substringBeforeLast('.') ?: throw SaveSlotLoadException("Could not determine ROM file name")
        val saveStateName = "$romFileName.ml${saveState.slot}"
        val saveStateFile = saveStateDirectoryDocument.findFile(saveStateName)

        saveStateFile?.delete()
        saveStateScreenshotProvider.deleteRomSaveStateScreenshot(rom, saveState)
    }
}