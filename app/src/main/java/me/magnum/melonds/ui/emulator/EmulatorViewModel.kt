package me.magnum.melonds.ui.emulator

import android.content.Context
import androidx.documentfile.provider.DocumentFile
import androidx.hilt.lifecycle.ViewModelInject
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import io.reactivex.Single
import io.reactivex.disposables.CompositeDisposable
import me.magnum.melonds.domain.model.*
import me.magnum.melonds.domain.repositories.CheatsRepository
import me.magnum.melonds.domain.repositories.RomsRepository
import me.magnum.melonds.domain.repositories.SettingsRepository
import me.magnum.melonds.extensions.addTo
import me.magnum.melonds.utils.FileUtils
import java.io.File
import java.util.*

class EmulatorViewModel @ViewModelInject constructor(
        private val context: Context,
        private val settingsRepository: SettingsRepository,
        private val romsRepository: RomsRepository,
        private val cheatsRepository: CheatsRepository
) : ViewModel() {
    private val disposables = CompositeDisposable()

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

    fun getEmulatorConfigurationForRom(rom: Rom): EmulatorConfiguration {
        val baseConfiguration = settingsRepository.getEmulatorConfiguration()
        return EmulatorConfiguration(
                baseConfiguration.dsConfigDirectory,
                baseConfiguration.dsiConfigDirectory,
                baseConfiguration.fastForwardSpeedMultiplier,
                baseConfiguration.useJit,
                getRomOptionOrDefault(rom.config.runtimeConsoleType, baseConfiguration.consoleType),
                baseConfiguration.soundEnabled,
                getRomOptionOrDefault(rom.config.runtimeMicSource, baseConfiguration.micSource),
                baseConfiguration.rendererConfiguration
        )
    }

    fun getEmulatorConfigurationForFirmware(consoleType: ConsoleType): EmulatorConfiguration {
        return settingsRepository.getEmulatorConfiguration().copy(
                consoleType = consoleType
        )
    }

    private fun <T, U> getRomOptionOrDefault(romOption: T, default: U): U where T : RuntimeEnum<T, U> {
        return if (romOption.getDefault() == romOption)
            default
        else
            romOption.getValue()
    }

    fun getRomEnabledCheats(romInfo: RomInfo): LiveData<List<Cheat>> {
        val liveData = MutableLiveData<List<Cheat>>()

        if (!settingsRepository.areCheatsEnabled()) {
            liveData.value = emptyList()
        } else {
            cheatsRepository.getRomEnabledCheats(romInfo).subscribe { cheats ->
                liveData.postValue(cheats)
            }.addTo(disposables)
        }

        return liveData
    }

    override fun onCleared() {
        super.onCleared()
        disposables.clear()
    }
}