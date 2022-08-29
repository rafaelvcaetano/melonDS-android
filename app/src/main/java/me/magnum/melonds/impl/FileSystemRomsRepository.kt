package me.magnum.melonds.impl

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import io.reactivex.disposables.CompositeDisposable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.magnum.melonds.common.romprocessors.RomFileProcessorFactory
import me.magnum.melonds.domain.model.Rom
import me.magnum.melonds.domain.model.RomConfig
import me.magnum.melonds.domain.model.RomScanningStatus
import me.magnum.melonds.domain.repositories.RomsRepository
import me.magnum.melonds.domain.repositories.SettingsRepository
import me.magnum.melonds.extensions.addTo
import me.magnum.melonds.utils.FileUtils
import me.magnum.melonds.utils.SubjectSharedFlow
import java.io.File
import java.io.FileReader
import java.io.OutputStreamWriter
import java.lang.reflect.Type
import java.util.*
import java.util.concurrent.atomic.AtomicBoolean

class FileSystemRomsRepository(
        private val context: Context,
        private val gson: Gson,
        private val settingsRepository: SettingsRepository,
        private val romFileProcessorFactory: RomFileProcessorFactory
) : RomsRepository {

    companion object {
        private const val TAG = "FSRomsRepository"
        private const val ROM_DATA_FILE = "rom_data.json"
    }

    private val coroutineScope = CoroutineScope(Dispatchers.Main)
    private val disposables = CompositeDisposable()
    private val romListType: Type = object : TypeToken<List<Rom>>(){}.type
    private val romsChannel = SubjectSharedFlow<List<Rom>>()
    private val scanningStatusSubject = MutableStateFlow(RomScanningStatus.NOT_SCANNING)
    private val roms: ArrayList<Rom> = ArrayList()
    private var areRomsLoaded = AtomicBoolean(false)

    init {
        coroutineScope.launch {
            romsChannel.onEach {
                saveRomData(it)
            }.collect()
        }

        settingsRepository.observeRomSearchDirectories()
                .subscribe { directories -> onRomSearchDirectoriesChanged(directories) }
                .addTo(disposables)
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
        return getRoms().first().find { rom ->
            rom.uri == uri
        }
    }

    override fun updateRomConfig(rom: Rom, romConfig: RomConfig) {
        val romIndex = roms.indexOf(rom)
        if (romIndex < 0)
            return

        roms[romIndex].config = romConfig
        onRomsChanged()
    }

    override fun setRomLastPlayed(rom: Rom, lastPlayed: Date) {
        val romIndex = roms.indexOf(rom)
        if (romIndex < 0)
            return

        rom.lastPlayed = lastPlayed
        roms[romIndex] = rom
        onRomsChanged()
    }

    override fun rescanRoms() {
        coroutineScope.launch(Dispatchers.IO) {
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
        if (roms.contains(rom))
            return

        roms.add(rom)
        onRomsChanged()
    }

    private fun removeRom(rom: Rom, notifyChanged: Boolean = true) {
        if (roms.remove(rom) && notifyChanged) {
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

    private suspend fun loadCachedRoms() = withContext(Dispatchers.IO) {
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
            gson.fromJson<List<Rom>>(FileReader(cacheFile), romListType)
        }.getOrElse { emptyList() }
    }

    private fun saveRomData(romData: List<Rom>) {
        val cacheFile = File(context.filesDir, ROM_DATA_FILE)

        try {
            val romsJson = gson.toJson(romData)

            val output = OutputStreamWriter(cacheFile.outputStream())
            output.write(romsJson)
            output.close()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save ROM data", e)
        }
    }
}