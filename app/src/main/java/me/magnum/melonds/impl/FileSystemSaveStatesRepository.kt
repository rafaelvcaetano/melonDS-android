package me.magnum.melonds.impl

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
    private val uriHandler: UriHandler
) : SaveStatesRepository {

    override fun getRomSaveStates(rom: Rom): List<SaveStateSlot> {
        val saveStateDirectoryUri = settingsRepository.getSaveStateDirectory(rom) ?: return emptyList()
        val saveStateDirectoryDocument = uriHandler.getUriTreeDocument(saveStateDirectoryUri) ?: return emptyList()
        val romDocument = uriHandler.getUriDocument(rom.uri)!!
        val romFileName = romDocument.name?.substringBeforeLast('.') ?: return emptyList()

        val saveStateSlots = Array(8) {
            SaveStateSlot(it + 1, false, null)
        }
        val fileNameRegex = "${Regex.escape(romFileName)}\\.ml[1-8]".toRegex()
        saveStateDirectoryDocument.listFiles().forEach {
            val fileName = it.name
            if (fileName?.matches(fileNameRegex) == true) {
                val slot = fileName.last().minus('0')
                saveStateSlots[slot - 1] = SaveStateSlot(slot, true, Date(it.lastModified()))
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
    }
}