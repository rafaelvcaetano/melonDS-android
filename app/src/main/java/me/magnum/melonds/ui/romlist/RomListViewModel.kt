package me.magnum.melonds.ui.romlist

import android.net.Uri
import android.provider.DocumentsContract
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.magnum.melonds.common.DirectoryAccessValidator
import me.magnum.melonds.common.Permission
import me.magnum.melonds.common.UriPermissionManager
import me.magnum.melonds.domain.model.ConfigurationDirResult
import me.magnum.melonds.domain.model.ConsoleType
import me.magnum.melonds.domain.model.SortingMode
import me.magnum.melonds.domain.model.SortingOrder
import me.magnum.melonds.domain.model.rom.Rom
import me.magnum.melonds.domain.model.rom.RomDirectoryScanStatus
import me.magnum.melonds.domain.model.rom.config.RuntimeConsoleType
import me.magnum.melonds.domain.repositories.RomsRepository
import me.magnum.melonds.domain.repositories.SettingsRepository
import me.magnum.melonds.domain.services.ConfigurationDirectoryVerifier
import me.magnum.melonds.impl.RomIconProvider
import me.magnum.melonds.utils.EventSharedFlow
import me.magnum.melonds.utils.SubjectSharedFlow
import me.magnum.melonds.ui.romlist.RomBrowserEntry
import me.magnum.melonds.ui.romlist.RomBrowserUiState
import java.text.Normalizer
import java.util.Calendar
import javax.inject.Inject

@HiltViewModel
class RomListViewModel @Inject constructor(
    private val romsRepository: RomsRepository,
    private val settingsRepository: SettingsRepository,
    private val romIconProvider: RomIconProvider,
    private val configurationDirectoryVerifier: ConfigurationDirectoryVerifier,
    private val uriPermissionManager: UriPermissionManager,
    private val directoryAccessValidator: DirectoryAccessValidator
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    private val _sortingMode = MutableStateFlow(settingsRepository.getRomSortingMode())
    private val _sortingOrder = MutableStateFlow(settingsRepository.getRomSortingOrder())

    private val _hasSearchDirectories = SubjectSharedFlow<Boolean>()
    val hasSearchDirectories: Flow<Boolean> = _hasSearchDirectories

    private val _invalidDirectoryAccessEvent = EventSharedFlow<Unit>()
    val invalidDirectoryAccessEvent: Flow<Unit> = _invalidDirectoryAccessEvent

    val onRomIconFilteringChanged = settingsRepository.observeRomIconFiltering()

    val romScanningStatus = romsRepository.getRomScanningStatus()

    private val romsWithParents = MutableStateFlow<List<RomWithParent>>(emptyList())
    private val rootDirectories = MutableStateFlow<List<RootDirectory>>(emptyList())
    private val navigationStack = MutableStateFlow<List<BrowserLocation>>(listOf(BrowserLocation.VirtualRoot))
    private val _browserState = MutableStateFlow(
        RomBrowserUiState(
            entries = emptyList(),
            breadcrumbs = emptyList(),
            canNavigateUp = false,
            isSearchActive = false,
            isAtVirtualRoot = true
        )
    )
    val browserState = _browserState.asStateFlow()
    private val _directoryStatusUi = MutableStateFlow<List<DirectoryCacheStatusUi>>(emptyList())
    val directoryStatusUi = _directoryStatusUi.asStateFlow()

    init {
        viewModelScope.launch {
            settingsRepository.observeRomSearchDirectories()
                .distinctUntilChanged()
                .collect { directories ->
                    _hasSearchDirectories.tryEmit(directories.isNotEmpty())

                    val roots = directories.mapNotNull { directory ->
                        runCatching {
                            val docId = DocumentsContract.getTreeDocumentId(directory)
                            RootDirectory(
                                uri = directory,
                                docId = docId,
                                displayName = extractDirectoryDisplayName(docId),
                                relativePath = extractRelativePath(docId)
                            )
                        }.getOrNull()
                    }

                    rootDirectories.value = roots
                    ensureNavigationStackForRoots(roots)
                }
        }

        viewModelScope.launch {
            romsRepository.getRoms().collect { romList ->
                val romsWithDocIds = romList.map { rom ->
                    val parentDocId = rom.parentTreeUri?.let { runCatching { DocumentsContract.getDocumentId(it) }.getOrNull() }
                    RomWithParent(rom, parentDocId)
                }
                romsWithParents.value = romsWithDocIds
            }
        }

        viewModelScope.launch {
            combine(
                romsWithParents,
                _searchQuery,
                navigationStack,
                rootDirectories,
                _sortingMode,
                _sortingOrder
            ) { values: Array<Any?> ->
                @Suppress("UNCHECKED_CAST")
                buildBrowserState(
                    values[0] as List<RomWithParent>,
                    values[1] as String,
                    values[2] as List<BrowserLocation>,
                    values[3] as List<RootDirectory>,
                    values[4] as SortingMode,
                    values[5] as SortingOrder
                )
            }.collect { state ->
                _browserState.value = state
            }
        }

        viewModelScope.launch {
            combine(
                rootDirectories,
                romsRepository.observeRomDirectoryScanStatuses()
            ) { roots, statuses ->
                buildDirectoryStatusUi(roots, statuses)
            }.collect { statusUi ->
                _directoryStatusUi.value = statusUi
            }
        }
    }

    fun refreshRoms() {
        romsRepository.rescanRoms()
    }

    fun setRomLastPlayedNow(rom: Rom) {
        romsRepository.setRomLastPlayed(rom, Calendar.getInstance().time)
    }

    fun setRomSearchQuery(query: String?) {
        _searchQuery.tryEmit(Normalizer.normalize(query ?: "", Normalizer.Form.NFD).replace("[^\\p{ASCII}]", ""))
    }

    fun openFolder(docId: String) {
        val stack = navigationStack.value.toMutableList()
        val lastLocation = stack.lastOrNull()
        if (lastLocation is BrowserLocation.Directory && lastLocation.docId == docId) {
            return
        }
        stack.add(BrowserLocation.Directory(docId))
        navigationStack.value = stack
    }

    fun navigateUp() {
        val stack = navigationStack.value
        if (stack.size <= 1) {
            return
        }
        navigationStack.value = stack.dropLast(1)
    }

    fun setRomSorting(sortingMode: SortingMode) {
        if (sortingMode == _sortingMode.value) {
            val newSortingOrder = if (_sortingOrder.value == SortingOrder.ASCENDING)
                SortingOrder.DESCENDING
            else
                SortingOrder.ASCENDING

            settingsRepository.setRomSortingOrder(_sortingOrder.value)
            _sortingOrder.value = newSortingOrder
        } else {
            settingsRepository.setRomSortingMode(sortingMode)
            settingsRepository.setRomSortingOrder(sortingMode.defaultOrder)

            _sortingMode.value = sortingMode
            _sortingOrder.value = sortingMode.defaultOrder
        }
    }

    fun getConsoleConfigurationDirResult(consoleType: ConsoleType): ConfigurationDirResult {
        return configurationDirectoryVerifier.checkConsoleConfigurationDirectory(consoleType)
    }

    fun getRomConfigurationDirStatus(rom: Rom): ConfigurationDirResult {
        val willUseInternalFirmware = !settingsRepository.useCustomBios() && rom.config.runtimeConsoleType == RuntimeConsoleType.DEFAULT
        if (willUseInternalFirmware) {
            return ConfigurationDirResult(ConsoleType.DS, ConfigurationDirResult.Status.VALID, emptyArray(), emptyArray())
        }

        val romTargetConsoleType = rom.config.runtimeConsoleType.targetConsoleType ?: settingsRepository.getDefaultConsoleType()
        return getConsoleConfigurationDirResult(romTargetConsoleType)
    }

    fun addRomSearchDirectory(directoryUri: Uri) {
        val accessValidationResult = directoryAccessValidator.getDirectoryAccessForPermission(directoryUri, Permission.READ_WRITE)

        if (accessValidationResult == DirectoryAccessValidator.DirectoryAccessResult.OK) {
            uriPermissionManager.persistDirectoryPermissions(directoryUri, Permission.READ_WRITE)
            settingsRepository.addRomSearchDirectory(directoryUri)
        } else {
            _invalidDirectoryAccessEvent.tryEmit(Unit)
        }
    }

    /**
     * Sets the DS BIOS directory to the given one if its access is validated.
     *
     * @return True if the directory access is validated and the directory is updated. False otherwise.
     */
    fun setDsBiosDirectory(uri: Uri): Boolean {
        val accessValidationResult = directoryAccessValidator.getDirectoryAccessForPermission(uri, Permission.READ_WRITE)

        return if (accessValidationResult == DirectoryAccessValidator.DirectoryAccessResult.OK) {
            uriPermissionManager.persistDirectoryPermissions(uri, Permission.READ_WRITE)
            settingsRepository.setDsBiosDirectory(uri)
            true
        } else {
            _invalidDirectoryAccessEvent.tryEmit(Unit)
            false
        }
    }

    /**
     * Sets the DSi BIOS directory to the given one if its access is validated.
     *
     * @return True if the directory access is validated and the directory is updated. False otherwise.
     */
    fun setDsiBiosDirectory(uri: Uri): Boolean {
        val accessValidationResult = directoryAccessValidator.getDirectoryAccessForPermission(uri, Permission.READ_WRITE)

        return if (accessValidationResult == DirectoryAccessValidator.DirectoryAccessResult.OK) {
            uriPermissionManager.persistDirectoryPermissions(uri, Permission.READ_WRITE)
            settingsRepository.setDsiBiosDirectory(uri)
            true
        } else {
            _invalidDirectoryAccessEvent.tryEmit(Unit)
            false
        }
    }

    suspend fun getRomIcon(rom: Rom): RomIcon {
        val romIconBitmap = romIconProvider.getRomIcon(rom)
        val iconFiltering = settingsRepository.getRomIconFiltering()
        return RomIcon(romIconBitmap, iconFiltering)
    }

    private suspend fun buildBrowserState(
        roms: List<RomWithParent>,
        query: String,
        navigationStack: List<BrowserLocation>,
        roots: List<RootDirectory>,
        sortingMode: SortingMode,
        sortingOrder: SortingOrder
    ): RomBrowserUiState = withContext(Dispatchers.Default) {
        if (roots.isEmpty()) {
            return@withContext RomBrowserUiState(
                entries = emptyList(),
                breadcrumbs = emptyList(),
                canNavigateUp = false,
                isSearchActive = query.isNotEmpty(),
                isAtVirtualRoot = true
            )
        }

        val sortedRoms = sortRoms(roms, sortingMode, sortingOrder)

        if (query.isNotEmpty()) {
            val filtered = filterRoms(sortedRoms, query)
            return@withContext RomBrowserUiState(
                entries = filtered.map { RomBrowserEntry.RomItem(it.rom) },
                breadcrumbs = emptyList(),
                canNavigateUp = false,
                isSearchActive = true,
                isAtVirtualRoot = false
            )
        }

        val directoryNodes = buildDirectoryNodes(sortedRoms, roots)
        val baseLocation = defaultLocationForRoots(roots)
        val effectiveStack = if (navigationStack.isEmpty()) listOf(baseLocation) else navigationStack
        val currentLocation = effectiveStack.lastOrNull() ?: baseLocation
        val isVirtualRoot = currentLocation is BrowserLocation.VirtualRoot

        val entries = if (isVirtualRoot) {
            roots.sortedBy { it.displayName.lowercase() }
                .map {
                    RomBrowserEntry.Folder(
                        docId = it.docId,
                        name = it.displayName,
                        relativePath = it.relativePath,
                        isRoot = true
                    )
                }
        } else {
            val docId = (currentLocation as BrowserLocation.Directory).docId
            val node = directoryNodes[docId] ?: createPlaceholderNode(docId, roots)
            val childFolders = node.childDirectories
                .mapNotNull { directoryNodes[it] }
                .sortedBy { it.displayName.lowercase() }
                .map {
                    RomBrowserEntry.Folder(
                        docId = it.docId,
                        name = it.displayName,
                        relativePath = it.relativePath,
                        isRoot = it.docId == it.root.docId
                    )
                }
            val romEntries = sortedRoms.filter { it.parentDocId == docId }.map { RomBrowserEntry.RomItem(it.rom) }
            childFolders + romEntries
        }

        val breadcrumbs = if (isVirtualRoot) {
            emptyList()
        } else {
            val docId = (currentLocation as BrowserLocation.Directory).docId
            buildBreadcrumbs(docId, directoryNodes, roots)
        }

        RomBrowserUiState(
            entries = entries,
            breadcrumbs = breadcrumbs,
            canNavigateUp = effectiveStack.size > 1,
            isSearchActive = false,
            isAtVirtualRoot = isVirtualRoot
        )
    }

    private fun sortRoms(roms: List<RomWithParent>, sortingMode: SortingMode, sortingOrder: SortingOrder): List<RomWithParent> {
        val comparator = when (sortingMode) {
            SortingMode.ALPHABETICALLY -> buildAlphabeticalRomComparator(sortingOrder)
            SortingMode.RECENTLY_PLAYED -> buildRecentlyPlayedRomComparator(sortingOrder)
        }
        return roms.sortedWith { o1, o2 -> comparator.compare(o1.rom, o2.rom) }
    }

    private fun filterRoms(roms: List<RomWithParent>, query: String): List<RomWithParent> {
        return roms.filter { rom ->
            val normalizedName = Normalizer.normalize(rom.rom.name, Normalizer.Form.NFD).replace("[^\\p{ASCII}]", "")
            val normalizedPath = Normalizer.normalize(rom.rom.fileName, Normalizer.Form.NFD).replace("[^\\p{ASCII}]", "")

            normalizedName.contains(query, true) || normalizedPath.contains(query, true)
        }
    }

    private fun buildDirectoryNodes(roms: List<RomWithParent>, roots: List<RootDirectory>): Map<String, DirectoryNode> {
        val nodes = mutableMapOf<String, DirectoryNode>()

        roots.forEach { root ->
            nodes[root.docId] = DirectoryNode(
                root = root,
                docId = root.docId,
                parentDocId = null,
                displayName = root.displayName,
                relativePath = root.relativePath,
                childDirectories = mutableSetOf()
            )
        }

        roms.forEach { rom ->
            val parentDocId = rom.parentDocId ?: return@forEach
            val root = findRootForDocId(parentDocId, roots) ?: return@forEach

            var currentDocId: String? = parentDocId
            while (currentDocId != null) {
                val parentOfCurrent = getParentDocId(currentDocId, root.docId)
                nodes.getOrPut(currentDocId) {
                    DirectoryNode(
                        root = root,
                        docId = currentDocId,
                        parentDocId = parentOfCurrent,
                        displayName = extractDirectoryDisplayName(currentDocId),
                        relativePath = buildRelativePath(root, currentDocId),
                        childDirectories = mutableSetOf()
                    )
                }

                if (parentOfCurrent != null) {
                    val parentNode = nodes.getOrPut(parentOfCurrent) {
                        DirectoryNode(
                            root = root,
                            docId = parentOfCurrent,
                            parentDocId = getParentDocId(parentOfCurrent, root.docId),
                            displayName = extractDirectoryDisplayName(parentOfCurrent),
                            relativePath = buildRelativePath(root, parentOfCurrent),
                            childDirectories = mutableSetOf()
                        )
                    }
                    parentNode.childDirectories.add(currentDocId)
                }

                currentDocId = parentOfCurrent
            }
        }

        return nodes
    }

    private fun ensureNavigationStackForRoots(roots: List<RootDirectory>) {
        val currentStack = navigationStack.value
        val preservedLocations = currentStack.filterIsInstance<BrowserLocation.Directory>()
            .map { it.docId }
            .filter { docId -> roots.any { matchesRoot(docId, it.docId) } }

        val newStack = mutableListOf<BrowserLocation>()
        when {
            roots.isEmpty() -> newStack.add(BrowserLocation.VirtualRoot)
            roots.size == 1 -> {
                if (preservedLocations.isEmpty()) {
                    newStack.add(BrowserLocation.Directory(roots.first().docId))
                } else {
                    preservedLocations.forEach { newStack.add(BrowserLocation.Directory(it)) }
                }
            }
            else -> {
                newStack.add(BrowserLocation.VirtualRoot)
                preservedLocations.forEach { newStack.add(BrowserLocation.Directory(it)) }
            }
        }

        if (newStack.isEmpty()) {
            newStack.add(BrowserLocation.VirtualRoot)
        }

        if (newStack != currentStack) {
            this.navigationStack.value = newStack
        }
    }

    private fun defaultLocationForRoots(roots: List<RootDirectory>): BrowserLocation {
        return if (roots.size == 1) {
            BrowserLocation.Directory(roots.first().docId)
        } else {
            BrowserLocation.VirtualRoot
        }
    }

    private fun createPlaceholderNode(docId: String, roots: List<RootDirectory>): DirectoryNode {
        val root = findRootForDocId(docId, roots) ?: roots.first()
        return DirectoryNode(
            root = root,
            docId = docId,
            parentDocId = getParentDocId(docId, root.docId),
            displayName = extractDirectoryDisplayName(docId),
            relativePath = buildRelativePath(root, docId),
            childDirectories = mutableSetOf()
        )
    }

    private fun buildBreadcrumbs(docId: String, nodes: Map<String, DirectoryNode>, roots: List<RootDirectory>): List<String> {
        val names = mutableListOf<String>()
        val root = findRootForDocId(docId, roots)
        var currentDocId: String? = docId
        while (currentDocId != null) {
            val node = nodes[currentDocId]
            if (node != null) {
                names.add(node.displayName)
                currentDocId = node.parentDocId
            } else {
                names.add(extractDirectoryDisplayName(currentDocId))
                currentDocId = if (root != null) getParentDocId(currentDocId, root.docId) else null
            }
        }
        return names.reversed()
    }

    private fun matchesRoot(docId: String, rootDocId: String): Boolean {
        return docId == rootDocId || docId.startsWith("$rootDocId/")
    }

    private fun findRootForDocId(docId: String, roots: List<RootDirectory>): RootDirectory? {
        return roots.firstOrNull { matchesRoot(docId, it.docId) }
    }

    private fun extractDirectoryDisplayName(docId: String): String {
        val path = extractRelativePath(docId)
        val segment = path.substringAfterLast('/', path)
        return segment.ifEmpty { path.ifEmpty { docId } }
    }

    private fun extractRelativePath(docId: String): String {
        return Uri.decode(docId.substringAfter(':', docId))
    }

    private fun buildRelativePath(root: RootDirectory, docId: String): String {
        val rootPath = extractRelativePath(root.docId)
        val docPath = extractRelativePath(docId)
        if (docId == root.docId) {
            return docPath
        }
        return if (rootPath.isNotEmpty() && docPath.startsWith("$rootPath/")) {
            docPath.removePrefix("$rootPath/")
        } else {
            docPath
        }
    }

    private fun getParentDocId(docId: String, rootDocId: String): String? {
        if (docId == rootDocId) {
            return null
        }
        val separatorIndex = docId.lastIndexOf('/')
        if (separatorIndex == -1) {
            return rootDocId
        }
        val parentDocId = docId.substring(0, separatorIndex)
        return if (parentDocId.length < rootDocId.length) {
            rootDocId
        } else {
            parentDocId
        }
    }

    private fun buildAlphabeticalRomComparator(sortingOrder: SortingOrder): Comparator<Rom> {
        return if (sortingOrder == SortingOrder.ASCENDING) {
            Comparator { o1: Rom, o2: Rom ->
                o1.name.compareTo(o2.name)
            }
        } else {
            Comparator { o1: Rom, o2: Rom ->
                o2.name.compareTo(o1.name)
            }
        }
    }

    private fun buildRecentlyPlayedRomComparator(sortingOrder: SortingOrder): Comparator<Rom> {
        return if (sortingOrder == SortingOrder.ASCENDING) {
            Comparator { o1: Rom, o2: Rom ->
                when {
                    o1.lastPlayed == null -> -1
                    o2.lastPlayed == null -> 1
                    else -> o1.lastPlayed!!.compareTo(o2.lastPlayed)
                }
            }
        } else {
            Comparator { o1: Rom, o2: Rom ->
                when {
                    o2.lastPlayed == null -> -1
                    o1.lastPlayed == null -> 1
                    else -> o2.lastPlayed!!.compareTo(o1.lastPlayed)
                }
            }
        }
    }

    private fun buildDirectoryStatusUi(
        roots: List<RootDirectory>,
        statuses: List<RomDirectoryScanStatus>
    ): List<DirectoryCacheStatusUi> {
        val statusMap = statuses.associateBy { it.directoryUri.toString() }
        return roots.map { root ->
            val status = statusMap[root.uri.toString()]
            DirectoryCacheStatusUi(
                directoryName = root.displayName,
                lastScanTimestamp = status?.lastScanTimestamp,
                result = status?.result ?: RomDirectoryScanStatus.ScanResult.NOT_SCANNED
            )
        }
    }

    private data class RomWithParent(
        val rom: Rom,
        val parentDocId: String?
    )

    private data class RootDirectory(
        val uri: Uri,
        val docId: String,
        val displayName: String,
        val relativePath: String
    )

    private data class DirectoryNode(
        val root: RootDirectory,
        val docId: String,
        val parentDocId: String?,
        val displayName: String,
        val relativePath: String,
        val childDirectories: MutableSet<String>
    )

    private sealed interface BrowserLocation {
        data object VirtualRoot : BrowserLocation
        data class Directory(val docId: String) : BrowserLocation
    }

    data class DirectoryCacheStatusUi(
        val directoryName: String,
        val lastScanTimestamp: Long?,
        val result: RomDirectoryScanStatus.ScanResult
    )
}
