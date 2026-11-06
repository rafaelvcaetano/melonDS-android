package me.magnum.melonds.impl

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.util.Log
import android.util.AtomicFile
import androidx.documentfile.provider.DocumentFile
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
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
import java.io.FileOutputStream
import java.lang.reflect.Type
import java.util.Date
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.time.Duration

class FileSystemRomsRepository(
    private val context: Context,
    private val gson: Gson,
    private val settingsRepository: SettingsRepository,
    private val romFileProcessorFactory: RomFileProcessorFactory,
    private val fileAccessDispatcher: CoroutineDispatcher,
) : RomsRepository {

    companion object {
        private const val TAG = "FSRomsRepository"
        private const val ROM_DATA_FILE = "rom_data.json"
        private const val EXTERNAL_STORAGE_PROVIDER_AUTHORITY = "com.android.externalstorage.documents"
        private const val MAX_SCAN_DEPTH = 64
        private const val MAX_SCAN_DOCUMENTS = 100_000
    }

    private val coroutineScope = CoroutineScope(Dispatchers.IO)
    private val romListType: Type = object : TypeToken<List<RomDto>>(){}.type
    private val romsChannel = SubjectSharedFlow<List<Rom>>()
    private val scanningStatus = MutableStateFlow(RomScanningStatus.NOT_SCANNING)
    private val scanMutex = Mutex()
    private val roms: ArrayList<Rom> = ArrayList()
    private var lastSavedRomData: String? = null
    private var areRomsLoaded = AtomicBoolean(false)

    init {
        coroutineScope.launch {
            romsChannel.collect {
                // An older queued emission must not restore a subsequently forgotten ROM.
                synchronized(roms) {
                    if (areRomsLoaded.get()) saveRomData(roms.toList())
                }
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
            coroutineScope.launch(fileAccessDispatcher) {
                scanRoms(loadCache = true)
            }
        }
        emitAll(romsChannel)
    }

    override fun getRomScanningStatus(): StateFlow<RomScanningStatus> {
        return scanningStatus.asStateFlow()
    }

    override suspend fun getRomAtPath(path: String): Rom? {
        return getRoms().first().find { rom ->
            val romPath = FileUtils.getAbsolutePathFromSAFUri(context, rom.uri)
            romPath == path
        }
    }

    override suspend fun getRomAtUri(uri: Uri): Rom? = withContext(fileAccessDispatcher) {
        val allRoms = getRoms().first()

        // Quick exact URI match first (no filename needed)
        allRoms.find { rom -> rom.uri == uri }?.let {
            return@withContext it
        }

        // Pre-filter by filename for performance
        val incomingFileName = runCatching { DocumentFile.fromSingleUri(context, uri)?.name }.getOrNull()
        val candidateRoms = if (incomingFileName != null) {
            allRoms.filter { it.fileName == incomingFileName }
        } else {
            allRoms
        }

        // Only stable identity is allowed to rebind to an existing cached URI. Provider-controlled
        // names and lengths describe content; they do not prove that two documents are the same.
        val cachedRom = findRomByDocumentId(candidateRoms, uri)
            ?: findRomByPath(candidateRoms, uri)

        if (cachedRom != null)
            return@withContext cachedRom

        // ROM is not known. Create a new ROM from the URI
        Log.i(TAG, "getRomAtUri: no cached ROM matched for $uri, falling back to ad-hoc import")
        runCatching {
            romFileProcessorFactory.getFileRomProcessorForDocument(uri)?.getRomFromUri(uri, null)
        }.getOrNull()
    }

    // Matches [uri] against a cached ROM's URI by comparing their SAF document IDs directly
    private fun findRomByDocumentId(roms: List<Rom>, uri: Uri): Rom? {
        if (uri.authority != EXTERNAL_STORAGE_PROVIDER_AUTHORITY) return null
        val incomingDocId = FileUtils.getDocumentIdOrNull(context, uri) ?: return null

        return roms.find { rom ->
            rom.uri.authority == EXTERNAL_STORAGE_PROVIDER_AUTHORITY &&
                FileUtils.getDocumentIdOrNull(context, rom.uri)?.equals(incomingDocId, ignoreCase = true) == true
        }
    }

    private fun findRomByPath(roms: List<Rom>, uri: Uri): Rom? {
        val incomingPath = FileUtils.getAbsolutePathFromSingleUri(context, uri) ?: return null
        return roms.find { rom ->
            FileUtils.getAbsolutePathFromSingleUri(context, rom.uri) == incomingPath
        }
    }

    override fun updateRomConfig(rom: Rom, romConfig: RomConfig) {
        synchronized(roms) {
            val romIndex = roms.indexOfFirst { it.hasSameFileAsRom(rom) }
            if (romIndex < 0)
                return

            roms[romIndex] = roms[romIndex].copy(config = romConfig)
            onRomsChanged()
        }
    }

    override fun setRomLastPlayed(rom: Rom, lastPlayed: Date) {
        synchronized(roms) {
            val romIndex = roms.indexOfFirst { it.hasSameFileAsRom(rom) }
            if (romIndex < 0)
                return

            roms[romIndex] = roms[romIndex].copy(lastPlayed = lastPlayed)
            onRomsChanged()
        }
    }

    override fun addRomPlayTime(rom: Rom, playTime: Duration) {
        synchronized(roms) {
            val romIndex = roms.indexOfFirst { it.hasSameFileAsRom(rom) }
            if (romIndex < 0)
                return

            val romInList = roms[romIndex]
            val updatedRom = romInList.copy(totalPlayTime = romInList.totalPlayTime + playTime)
            roms[romIndex] = updatedRom
            onRomsChanged()
        }
    }

    override fun rescanRoms() {
        coroutineScope.launch(fileAccessDispatcher) {
            scanRoms()
        }
    }

    override fun invalidateRoms() {
        synchronized(roms) {
            areRomsLoaded.set(false)
            roms.clear()
            AtomicFile(File(context.filesDir, ROM_DATA_FILE)).delete()
            lastSavedRomData = null
        }
    }

    private fun addRom(rom: Rom) {
        synchronized(roms) {
            val existingRom = roms.find { it.hasSameFileAsRom(rom) }
            if (existingRom == rom) {
                return
            }

            if (existingRom != null) {
                // ROM has different metadata. Update it
                val updatedRom = existingRom.copy(
                    name = rom.name,
                    developerName = rom.developerName,
                    isDsiWareTitle = rom.isDsiWareTitle,
                    retroAchievementsHash = rom.retroAchievementsHash,
                )
                roms.remove(existingRom)
                roms.add(updatedRom)
            } else {
                roms.add(rom)
            }

            onRomsChanged()
        }
    }

    override suspend fun forgetRom(rom: Rom): Boolean = withContext(fileAccessDispatcher) {
        getRoms().first()
        scanMutex.withLock {
            synchronized(roms) {
                val retained = roms.filterNot { it.hasSameFileAsRom(rom) }
                if (retained.size == roms.size) return@synchronized true
                if (!saveRomData(retained)) return@synchronized false
                roms.clear()
                roms.addAll(retained)
                onRomsChanged()
                true
            }
        }
    }

    private fun removeAllRoms() {
        synchronized(roms) {
            roms.clear()
            onRomsChanged()
        }
    }

    private fun onRomsChanged() {
        // Callers hold the list lock; later repository changes must not mutate this emission.
        romsChannel.tryEmit(roms.toList())
    }

    private suspend fun scanRoms(loadCache: Boolean = false) {
        // The file dispatcher may run multiple tasks or suspend: scan status has one owner at a time.
        scanMutex.withLock {
            scanningStatus.value = RomScanningStatus.SCANNING
            try {
                if (loadCache) {
                    val cachedRoms = getCachedRoms().filter { cachedRomStillExists(it.uri) }
                    synchronized(roms) {
                        roms.addAll(cachedRoms)
                        onRomsChanged()
                    }
                }
                scanForNewRoms().collect { addRom(it) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Keep the cache and the repository scope usable for the next refresh.
                Log.e(TAG, "Failed to scan ROMs", e)
            } finally {
                scanningStatus.value = RomScanningStatus.NOT_SCANNING
            }
        }
    }

    private fun cachedRomStillExists(uri: Uri): Boolean {
        if (uri.scheme != "content") {
            return DocumentFile.fromSingleUri(context, uri)?.exists() == true
        }
        // DocumentFile.exists() conflates a deleted document with a denied or unavailable provider.
        // Only a successful empty query proves deletion; unknown access must preserve user metadata.
        return try {
            context.contentResolver.query(uri, arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID), null, null, null)
                ?.use { it.count > 0 } ?: true
        } catch (e: Exception) {
            Log.w(TAG, "Could not check cached ROM; retaining metadata for retry", e)
            true
        }
    }

    private fun scanForNewRoms(): Flow<Rom> = flow {
        val traversal = ScanTraversal()
        for (directory in settingsRepository.getRomSearchDirectories()) {
            val documentFile = DocumentFile.fromTreeUri(context, directory)
            if (documentFile != null) {
                findCachedRomFiles(documentFile, this, traversal, 0)
            }
        }
    }

    private class ScanTraversal {
        val visitedDirectories = mutableSetOf<String>()
        var documents = 0
    }

    private suspend fun findCachedRomFiles(
        directory: DocumentFile,
        collector: FlowCollector<Rom>,
        traversal: ScanTraversal,
        depth: Int,
    ) {
        if (depth > MAX_SCAN_DEPTH) return
        val directoryKey = runCatching {
            "${directory.uri.authority}:${DocumentsContract.getDocumentId(directory.uri)}"
        }.getOrElse { directory.uri.toString() }
        if (!traversal.visitedDirectories.add(directoryKey)) return
        val files = directory.listFiles()
        for (file in files) {
            if (++traversal.documents > MAX_SCAN_DOCUMENTS) return
            if (file.isDirectory) {
                findCachedRomFiles(file, collector, traversal, depth + 1)
                continue
            }

            romFileProcessorFactory.getFileRomProcessorForDocument(file)?.let { fileRomProcessor ->
                fileRomProcessor.getRomFromUri(file.uri, directory.uri)?.let { collector.emit(it) }
            }
        }
    }

    private fun getCachedRoms(): List<Rom> {
        val cacheFile = File(context.filesDir, ROM_DATA_FILE)
        return runCatching {
            AtomicFile(cacheFile).openRead().bufferedReader().use { reader ->
                gson.fromJson<List<RomDto>>(reader, romListType).map { it.toModel() }
            }
        }.getOrElse { emptyList() }
    }

    private fun saveRomData(romData: List<Rom>): Boolean {
        val cacheFile = AtomicFile(File(context.filesDir, ROM_DATA_FILE))
        var stream: FileOutputStream? = null

        try {
            val romDtos = romData.map {
                RomDto.fromModel(it)
            }
            val romsJson = gson.toJson(romDtos)
            if (romsJson == lastSavedRomData && cacheFile.baseFile.isFile) return true

            stream = cacheFile.startWrite()
            stream.write(romsJson.toByteArray(Charsets.UTF_8))
            cacheFile.finishWrite(stream)
            lastSavedRomData = romsJson
            return true
        } catch (e: Exception) {
            cacheFile.failWrite(stream)
            Log.e(TAG, "Failed to save ROM data", e)
            return false
        }
    }
}
