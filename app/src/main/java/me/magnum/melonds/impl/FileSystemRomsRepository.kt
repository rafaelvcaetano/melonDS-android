package me.magnum.melonds.impl

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import android.provider.DocumentsContract
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
import me.magnum.melonds.impl.dtos.rom.RomDirectoryFileDto
import me.magnum.melonds.impl.dtos.rom.RomDirectoryStateDto
import me.magnum.melonds.domain.model.rom.RomDirectoryScanStatus
import me.magnum.melonds.utils.FileUtils
import me.magnum.melonds.utils.SubjectSharedFlow
import java.io.File
import java.io.FileReader
import java.io.OutputStreamWriter
import java.lang.reflect.Type
import java.security.MessageDigest
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
        private const val ROM_DIRECTORY_STATE_FILE = "rom_directory_state.json"
    }

    private val coroutineScope = CoroutineScope(Dispatchers.IO)
    private val romListType: Type = object : TypeToken<List<RomDto>>(){}.type
    private val directoryStateListType: Type = object : TypeToken<List<RomDirectoryStateDto>>(){}.type
    private val romsChannel = SubjectSharedFlow<List<Rom>>()
    private val scanningStatusSubject = MutableStateFlow(RomScanningStatus.NOT_SCANNING)
    private val roms: ArrayList<Rom> = ArrayList()
    private var areRomsLoaded = AtomicBoolean(false)
    private val directoryStatesLock = Any()
    private val directoryStates: MutableMap<String, DirectoryCacheState> = mutableMapOf()
    private val directoryScanStatuses: MutableMap<String, RomDirectoryScanStatus> = mutableMapOf()
    private val directoryScanStatusFlow = MutableStateFlow<List<RomDirectoryScanStatus>>(emptyList())
    @Volatile private var currentDirectoryUris: Map<String, Uri> = emptyMap()

    init {
        loadDirectoryStates()

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
        val newDirectoryMap = searchDirectories.associateBy { it.toString() }
        val previousDirectoryMap = currentDirectoryUris
        currentDirectoryUris = newDirectoryMap

        removeStaleDirectoryStates(newDirectoryMap.keys)

        val removedDirectoryKeys = previousDirectoryMap.keys - newDirectoryMap.keys
        val addedDirectoryKeys = newDirectoryMap.keys - previousDirectoryMap.keys

        val removedDirectoryUris = removedDirectoryKeys.mapNotNull { previousDirectoryMap[it] }.toSet()
        val addedDirectoryStrings = addedDirectoryKeys.toSet()

        if (!areRomsLoaded.get()) {
            return
        }

        if (removedDirectoryUris.isNotEmpty()) {
            removeRomsForDirectories(removedDirectoryUris)
        }

        if (addedDirectoryStrings.isNotEmpty()) {
            coroutineScope.launch {
                scanningStatusSubject.emit(RomScanningStatus.SCANNING)
                scanForNewRoms(targetDirectories = addedDirectoryStrings).collect {
                    addRom(it)
                }
                scanningStatusSubject.emit(RomScanningStatus.NOT_SCANNING)
            }
        }
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

    override fun observeRomDirectoryScanStatuses(): Flow<List<RomDirectoryScanStatus>> {
        return directoryScanStatusFlow.asStateFlow()
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

        val directoryCacheFile = File(context.filesDir, ROM_DIRECTORY_STATE_FILE)
        if (directoryCacheFile.isFile) {
            directoryCacheFile.delete()
        }

        synchronized(directoryStatesLock) {
            directoryStates.clear()
            directoryScanStatuses.clear()
        }

        directoryScanStatusFlow.value = emptyList()
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

    private fun removeRomsForDirectories(directoryUris: Set<Uri>) {
        if (directoryUris.isEmpty()) {
            return
        }

        val directoryDocIds = directoryUris.mapNotNull { uri ->
            runCatching { DocumentsContract.getTreeDocumentId(uri) }.getOrNull()
        }

        if (directoryDocIds.isEmpty()) {
            return
        }

        val removed = roms.removeAll { rom ->
            val parentUri = rom.parentTreeUri ?: return@removeAll false
            val parentDocId = runCatching { DocumentsContract.getDocumentId(parentUri) }.getOrNull() ?: return@removeAll false
            directoryDocIds.any { parentDocId.startsWith(it) }
        }

        if (removed) {
            onRomsChanged()
        }
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

    private fun scanForNewRoms(targetDirectories: Set<String>? = null): Flow<Rom> = flow {
        val directories = settingsRepository.getRomSearchDirectories()
        for (directory in directories) {
            val directoryString = directory.toString()
            if (targetDirectories != null && !targetDirectories.contains(directoryString)) {
                continue
            }
            val documentFile = DocumentFile.fromTreeUri(context, directory)
            if (documentFile != null) {
                processDirectory(directory, documentFile, this)
            }
        }
    }

    private suspend fun processDirectory(directoryUri: Uri, directoryDocument: DocumentFile, collector: FlowCollector<Rom>) {
        val fileStates = collectDirectoryFileStates(directoryDocument)
        val directoryHash = computeDirectoryHash(fileStates)
        val cachedState = getDirectoryState(directoryUri)
        val now = System.currentTimeMillis()

        if (cachedState != null && cachedState.hash == directoryHash) {
            val refreshedState = cachedState.copy(lastScanned = now)
            updateDirectoryState(refreshedState, RomDirectoryScanStatus.ScanResult.UNCHANGED)
            return
        }

        val cachedFiles = cachedState?.files ?: emptyMap()
        val currentFiles = fileStates.associateBy { it.uri.toString() }

        val updatedFiles = fileStates.filter { fileState ->
            val cachedFile = cachedFiles[fileState.uri.toString()]
            cachedFile == null || cachedFile.lastModified != fileState.lastModified || cachedFile.size != fileState.size
        }

        val removedFiles = cachedFiles.keys - currentFiles.keys
        removeRomsByUriStrings(removedFiles)

        val updatedExistingUris = updatedFiles.mapNotNull { fileState ->
            fileState.uri.toString().takeIf { cachedFiles.containsKey(it) }
        }.toSet()
        removeRomsByUriStrings(updatedExistingUris)

        for (fileState in updatedFiles) {
            romFileProcessorFactory.getFileRomProcessorForDocument(fileState.documentFile)?.let { fileRomProcessor ->
                fileRomProcessor.getRomFromUri(fileState.uri, fileState.parentUri)?.let { collector.emit(it) }
            }
        }

        val newCacheState = DirectoryCacheState(
            directoryUri = directoryUri,
            hash = directoryHash,
            lastScanned = now,
            files = currentFiles.mapValues { (_, fileState) ->
                DirectoryCacheFile(
                    uri = fileState.uri,
                    lastModified = fileState.lastModified,
                    size = fileState.size
                )
            }
        )

        val scanResult = if (updatedFiles.isEmpty() && removedFiles.isEmpty()) {
            RomDirectoryScanStatus.ScanResult.UNCHANGED
        } else {
            RomDirectoryScanStatus.ScanResult.UPDATED
        }

        updateDirectoryState(newCacheState, scanResult)
    }

    private fun collectDirectoryFileStates(rootDirectory: DocumentFile): List<DirectoryFileState> {
        val files = mutableListOf<DirectoryFileState>()
        collectDirectoryFileStatesRecursive(rootDirectory, files)
        return files
    }

    private fun collectDirectoryFileStatesRecursive(currentDirectory: DocumentFile, accumulator: MutableList<DirectoryFileState>) {
        val files = try {
            currentDirectory.listFiles()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to list files for directory ${currentDirectory.uri}", e)
            return
        }
        for (file in files) {
            if (file.isDirectory) {
                collectDirectoryFileStatesRecursive(file, accumulator)
                continue
            }

            val fileProcessor = romFileProcessorFactory.getFileRomProcessorForDocument(file)
            if (fileProcessor != null) {
                accumulator.add(
                    DirectoryFileState(
                        uri = file.uri,
                        parentUri = currentDirectory.uri,
                        lastModified = file.lastModified().coerceAtLeast(0),
                        size = file.length().coerceAtLeast(0),
                        documentFile = file
                    )
                )
            }
        }
    }

    private fun computeDirectoryHash(fileStates: List<DirectoryFileState>): String {
        val digest = MessageDigest.getInstance("SHA-256")
        fileStates.sortedBy { it.uri.toString() }.forEach { state ->
            val entry = "${state.uri}|${state.lastModified}|${state.size}"
            digest.update(entry.toByteArray())
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun getDirectoryState(directoryUri: Uri): DirectoryCacheState? {
        return synchronized(directoryStatesLock) {
            directoryStates[directoryUri.toString()]
        }
    }

    private fun updateDirectoryState(state: DirectoryCacheState, scanResult: RomDirectoryScanStatus.ScanResult) {
        synchronized(directoryStatesLock) {
            directoryStates[state.directoryUri.toString()] = state
            directoryScanStatuses[state.directoryUri.toString()] = RomDirectoryScanStatus(
                directoryUri = state.directoryUri,
                lastScanTimestamp = state.lastScanned,
                result = scanResult
            )
            emitDirectoryScanStatusesLocked()
        }
        saveDirectoryStates()
    }

    private fun removeRomsByUriStrings(uriStrings: Set<String>) {
        if (uriStrings.isEmpty()) {
            return
        }

        if (roms.removeAll { uriStrings.contains(it.uri.toString()) }) {
            onRomsChanged()
        }
    }

    private fun removeStaleDirectoryStates(validDirectoryUris: Set<String>) {
        var hasChanged = false
        synchronized(directoryStatesLock) {
            val iterator = directoryStates.keys.iterator()
            while (iterator.hasNext()) {
                val key = iterator.next()
                if (!validDirectoryUris.contains(key)) {
                    iterator.remove()
                    directoryScanStatuses.remove(key)
                    hasChanged = true
                }
            }
            if (hasChanged) {
                emitDirectoryScanStatusesLocked()
            }
        }

        if (hasChanged) {
            saveDirectoryStates()
        }
    }

    private fun loadDirectoryStates() {
        val cacheFile = File(context.filesDir, ROM_DIRECTORY_STATE_FILE)
        if (!cacheFile.isFile) {
            return
        }

        runCatching {
            FileReader(cacheFile).use { reader ->
                gson.fromJson<List<RomDirectoryStateDto>>(reader, directoryStateListType)
            }
        }.onSuccess { stateDtos ->
            if (stateDtos != null) {
                synchronized(directoryStatesLock) {
                    directoryStates.clear()
                    directoryScanStatuses.clear()
                    stateDtos.forEach { dto ->
                        val state = dto.toCacheState()
                        directoryStates[state.directoryUri.toString()] = state
                        directoryScanStatuses[state.directoryUri.toString()] = RomDirectoryScanStatus(
                            directoryUri = state.directoryUri,
                            lastScanTimestamp = state.lastScanned.takeIf { it > 0 },
                            result = RomDirectoryScanStatus.ScanResult.UNCHANGED
                        )
                    }
                    emitDirectoryScanStatusesLocked()
                }
            }
        }.onFailure {
            Log.w(TAG, "Failed to load ROM directory cache", it)
        }
    }

    private fun emitDirectoryScanStatusesLocked() {
        directoryScanStatusFlow.value = directoryScanStatuses.values.sortedBy { it.directoryUri.toString() }
    }

    private fun saveDirectoryStates() {
        val directoryStateDtos = synchronized(directoryStatesLock) {
            directoryStates.values.map { it.toDto() }
        }

        val cacheFile = File(context.filesDir, ROM_DIRECTORY_STATE_FILE)
        try {
            val json = gson.toJson(directoryStateDtos)
            OutputStreamWriter(cacheFile.outputStream()).use {
                it.write(json)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save ROM directory cache", e)
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

    private data class DirectoryCacheState(
        val directoryUri: Uri,
        val hash: String,
        val lastScanned: Long,
        val files: Map<String, DirectoryCacheFile>
    ) {
        fun toDto(): RomDirectoryStateDto {
            return RomDirectoryStateDto(
                directoryUri = directoryUri.toString(),
                hash = hash,
                lastScanned = lastScanned,
                files = files.values.map {
                    RomDirectoryFileDto(
                        uri = it.uri.toString(),
                        lastModified = it.lastModified,
                        size = it.size
                    )
                }
            )
        }
    }

    private data class DirectoryCacheFile(
        val uri: Uri,
        val lastModified: Long,
        val size: Long
    )

    private data class DirectoryFileState(
        val uri: Uri,
        val parentUri: Uri,
        val lastModified: Long,
        val size: Long,
        val documentFile: DocumentFile
    )

    private fun RomDirectoryStateDto.toCacheState(): DirectoryCacheState {
        val fileEntries = files.associateBy({ it.uri }) {
            DirectoryCacheFile(
                uri = it.uri.toUri(),
                lastModified = it.lastModified,
                size = it.size
            )
        }
        return DirectoryCacheState(
            directoryUri = directoryUri.toUri(),
            hash = hash,
            lastScanned = lastScanned,
            files = fileEntries
        )
    }
}
