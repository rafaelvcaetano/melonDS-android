package me.magnum.melonds.ui.emulator

import androidx.lifecycle.ViewModel
import me.magnum.melonds.model.Rom
import me.magnum.melonds.model.SaveStateSlot
import me.magnum.melonds.repositories.SettingsRepository
import java.io.File
import java.util.*

class EmulatorViewModel(private val settingsRepository: SettingsRepository) : ViewModel() {
    fun getRomSaveStateSlots(rom: Rom): List<SaveStateSlot> {
        val saveStatePath = settingsRepository.getSaveStateDirectory(rom)
        val saveStateDirectory = File(saveStatePath)
        if (!saveStateDirectory.isDirectory) {
            // If the directory cannot be created, there's no point in returning slots
            if (!saveStateDirectory.mkdirs())
                return emptyList()
        }

        val romFileName = File(rom.path).nameWithoutExtension

        val saveStateSlots = mutableListOf<SaveStateSlot>()
        for (i in 1..8) {
            val saveStateFile = File(saveStateDirectory, "$romFileName.ml$i")
            if (saveStateFile.isFile)
                saveStateSlots.add(SaveStateSlot(i, true, saveStateFile.absolutePath, Date(saveStateFile.lastModified())))
            else
                saveStateSlots.add(SaveStateSlot(i, false, saveStateFile.absolutePath, null))
        }

        return saveStateSlots
    }
}