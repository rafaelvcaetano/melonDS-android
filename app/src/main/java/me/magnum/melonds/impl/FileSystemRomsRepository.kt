package me.magnum.melonds.impl

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import me.magnum.melonds.common.romprocessors.RomFileProcessorFactory
import me.magnum.melonds.domain.model.RomScanningStatus
import me.magnum.melonds.domain.model.rom.Rom
import me.magnum.melonds.domain.model.rom.config.RomConfig
import me.magnum.melonds.domain.repositories.RomsRepository
import me.magnum.melonds.domain.repositories.SettingsRepository
import me.magnum.melonds.impl.dtos.rom.RomDto
import me.magnum.melonds.utils.FileUtils
import me.magnum.melonds.utils.SubjectSharedFlow
import java.io.File
import java.io.FileReader
import java.io.OutputStreamWriter
import java.lang.reflect.Type
import java.util.Date
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.time.Duration

class FileSystemRomsRepository(
        private val context: Context,
        private val gson: Gson,
        private val settingsRepository: SettingsRepository,
        private val romFileProcessorFactory: RomFileProcessorFactory
) : RomsRepository {

    companion object {
        private const val TAG = "FSRomsRepository"
        private const val EXTERNAL_STORAGE_PROVIDER_AUTHORITY = "com.android.externalstorage.documents"
        private const val ROM_DATA_FILE = "rom_data.json"
    }

    private val coroutineScope = CoroutineScope(Dispatchers.IO)
    private val romListType: Type = object : TypeToken<List<RomDto>>(){}.type
    private val romsChannel = SubjectSharedFlow<List<Rom>>()
    private val scanningStatusSubject = MutableStateFlow(RomScanningStatus.NOT_SCANNING)
    private val roms: ArrayList<Rom> = ArrayList()
    private var areRomsLoaded = AtomicBoolean(false)

    init {
        coroutineScope.launch {
            romsChannel.collect {
                saveRomData(it)
            }
        }

        coroutineScope.launch {
            settingsRepository.observeRomSearchDirectories().collectLatest { directories ->
                onRomSearchDirectoriesChanged(directories)
            }
        }
    }

    private fun onRomSearchDirectoriesChanged(searchDirectories: Array<Uri>) {
        // If ROMs have not been loaded yet, there's no point in searching or discarding ROMs now.
        // They will be scanned once needed
        if (!areRomsLoaded.get())
            return

        // TODO: Check if existing ROMs are still found in the new directory(s). How can we do that reliably using URIs?
        removeAllRoms()
        rescanRoms()
    }

    override fun getRoms(): Flow<List<Rom>> = flow {
        if (areRomsLoaded.compareAndSet(false, true)) {
            coroutineScope.launch {
                loadCachedRoms()
            }
        }
        emitAll(romsChannel)
    }

    override fun getRomScanningStatus(): StateFlow<RomScanningStatus> {
        return scanningStatusSubject.asStateFlow()
    }

    override suspend fun getRomAtPath(path: String): Rom? {
        return getRoms().first().find { rom ->
            val romPath = FileUtils.getAbsolutePathFromSAFUri(context, rom.uri)
            romPath == path
        }
    }

    override suspend fun getRomAtUri(uri: Uri): Rom? {
        val exactRom = if (uri.authority == EXTERNAL_STORAGE_PROVIDER_AUTHORITY) {
            getRoms().first().find { rom ->
                rom.uri == uri
            }
        } else {
            // Try to find the ROM by obtaining the file path from the URI and checking against known ROMs. This may not always work since there are multiple entry points to
            // the user-accessible storage (/storage/emulated/0, /mnt/user/0, /sdcard). This can be explored further in the future to see if different path prefixes can be
            // removed to make this approach more reliable
            FileUtils.getAbsolutePathFromSingleUri(context, uri)?.let {
                getRomAtPath(it)
            }
        }

        if (exactRom != null)
            return exactRom

        // ROM is not known. Create a new ROM from the URI
        val externalRom = romFileProcessorFactory.getFileRomProcessorForDocument(uri)?.getRomFromUri(uri, null)
        return externalRom
    }

    override fun updateRomConfig(rom: Rom, romConfig: RomConfig) {
        val romIndex = roms.indexOfFirst { it.hasSameFileAsRom(rom) }
        if (romIndex < 0)
            return

        roms[romIndex].config = romConfig
        onRomsChanged()
    }

    override fun setRomLastPlayed(rom: Rom, lastPlayed: Date) {
        val romIndex = roms.indexOfFirst { it.hasSameFileAsRom(rom) }
        if (romIndex < 0)
            return

        rom.lastPlayed = lastPlayed
        roms[romIndex] = rom
        onRomsChanged()
    }

    override fun addRomPlayTime(rom: Rom, playTime: Duration) {
        val romIndex = roms.indexOfFirst { it.hasSameFileAsRom(rom) }
        if (romIndex < 0)
            return

        val romInList = roms[romIndex]
        val updatedRom = romInList.copy(totalPlayTime = romInList.totalPlayTime + playTime)
        roms[romIndex] = updatedRom
        onRomsChanged()
    }

    override fun rescanRoms() {
        coroutineScope.launch {
            scanningStatusSubject.emit(RomScanningStatus.SCANNING)

            scanForNewRoms().collect {
                addRom(it)
            }

            scanningStatusSubject.emit(RomScanningStatus.NOT_SCANNING)
        }
    }

    override fun invalidateRoms() {
        if (areRomsLoaded.compareAndSet(true, false)) {
            roms.clear()
        }

        val cacheFile = File(context.filesDir, ROM_DATA_FILE)
        if (cacheFile.isFile) {
            cacheFile.delete()
        }
    }

    private fun addRom(rom: Rom) {
        if (roms.any { it.hasSameFileAsRom(rom) })
            return

        roms.add(rom)
        onRomsChanged()
    }

    private fun removeRom(rom: Rom, notifyChanged: Boolean = true) {
        if (roms.removeAll { it.hasSameFileAsRom(rom) } && notifyChanged) {
            onRomsChanged()
        }
    }

    private fun removeAllRoms() {
        roms.clear()
        onRomsChanged()
    }

    private fun onRomsChanged() {
        romsChannel.tryEmit(roms)
    }

    private suspend fun loadCachedRoms() {
        scanningStatusSubject.emit(RomScanningStatus.SCANNING)

        val cachedRoms = getCachedRoms().filter {
            DocumentFile.fromSingleUri(context, it.uri)?.exists() == true
        }

        roms.addAll(cachedRoms)
        onRomsChanged()
        scanForNewRoms().collect {
            addRom(it)
        }

        scanningStatusSubject.emit(RomScanningStatus.NOT_SCANNING)
    }

    private fun scanForNewRoms(): Flow<Rom> = flow {
        for (directory in settingsRepository.getRomSearchDirectories()) {
            val documentFile = DocumentFile.fromTreeUri(context, directory)
            if (documentFile != null) {
                findCachedRomFiles(documentFile, this)
            }
        }
    }

    private suspend fun findCachedRomFiles(directory: DocumentFile, collector: FlowCollector<Rom>) {
        val files = directory.listFiles()
        for (file in files) {
            if (file.isDirectory) {
                findCachedRomFiles(file, collector)
                continue
            }

            romFileProcessorFactory.getFileRomProcessorForDocument(file)?.let { fileRomProcessor ->
                fileRomProcessor.getRomFromUri(file.uri, directory.uri)?.let { collector.emit(it) }
            }
        }
    }

    private fun getCachedRoms(): List<Rom> {
        val cacheFile = File(context.filesDir, ROM_DATA_FILE)
        if (!cacheFile.isFile) {
            return emptyList()
        }

        return runCatching {
            gson.fromJson<List<RomDto>>(FileReader(cacheFile), romListType).map {
                it.toModel()
            }
        }.getOrElse { emptyList() }
    }

    private fun saveRomData(romData: List<Rom>) {
        val cacheFile = File(context.filesDir, ROM_DATA_FILE)

        try {
            val romDtos = romData.map {
                RomDto.fromModel(it)
            }
            val romsJson = gson.toJson(romDtos)

            OutputStreamWriter(cacheFile.outputStream()).use {
                it.write(romsJson)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save ROM data", e)
        }
    }
}