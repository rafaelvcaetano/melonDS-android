package me.magnum.melonds.ui.emulator

import android.content.Context
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.ViewModel
import io.reactivex.Single
import me.magnum.melonds.domain.model.EmulatorConfiguration
import me.magnum.melonds.domain.model.Rom
import me.magnum.melonds.domain.model.RomConfig
import me.magnum.melonds.domain.model.SaveStateSlot
import me.magnum.melonds.domain.repositories.RomsRepository
import me.magnum.melonds.domain.repositories.SettingsRepository
import me.magnum.melonds.utils.FileUtils
import java.io.File
import java.util.*

class EmulatorViewModel(private val context: Context, private val settingsRepository: SettingsRepository, private val romsRepository: RomsRepository) : ViewModel() {
    fun getRomSaveStateSlots(rom: Rom): List<SaveStateSlot> {
        val saveStatePath = settingsRepository.getSaveStateDirectory(rom) ?: return emptyList()
        val saveStateDirectory = File(saveStatePath)
        if (!saveStateDirectory.isDirectory) {
            // If the directory cannot be created, there's no point in returning slots
            if (!saveStateDirectory.mkdirs())
                return emptyList()
        }

        val romDocument = DocumentFile.fromSingleUri(context, rom.uri)!!
        val romFileName = FileUtils.getFileNameWithoutExtensions(romDocument.name!!)

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

    fun getRomAtPath(path: String): Single<Rom> {
        val romDocument = DocumentFile.fromFile(File(path))
        return romsRepository.getRomAtPath(path).defaultIfEmpty(Rom(path, romDocument.uri, RomConfig())).toSingle()
    }

    fun getEmulatorConfiguration(): EmulatorConfiguration {
        return settingsRepository.getEmulatorConfiguration()
    }
}