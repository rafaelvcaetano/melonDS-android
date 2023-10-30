package me.magnum.melonds.ui.romlist

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.reactivex.Single
import io.reactivex.disposables.CompositeDisposable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import me.magnum.melonds.common.DirectoryAccessValidator
import me.magnum.melonds.common.Permission
import me.magnum.melonds.common.UriPermissionManager
import me.magnum.melonds.common.uridelegates.UriHandler
import me.magnum.melonds.domain.model.*
import me.magnum.melonds.domain.repositories.LayoutsRepository
import me.magnum.melonds.domain.repositories.RomsRepository
import me.magnum.melonds.domain.repositories.SettingsRepository
import me.magnum.melonds.domain.services.ConfigurationDirectoryVerifier
import me.magnum.melonds.extensions.addTo
import me.magnum.melonds.impl.RomIconProvider
import me.magnum.melonds.utils.EventSharedFlow
import me.magnum.melonds.utils.SubjectSharedFlow
import java.text.Normalizer
import java.util.*
import javax.inject.Inject

@HiltViewModel
class RomListViewModel @Inject constructor(
    private val romsRepository: RomsRepository,
    private val settingsRepository: SettingsRepository,
    private val layoutsRepository: LayoutsRepository,
    private val romIconProvider: RomIconProvider,
    private val configurationDirectoryVerifier: ConfigurationDirectoryVerifier,
    private val uriHandler: UriHandler,
    private val uriPermissionManager: UriPermissionManager,
    private val directoryAccessValidator: DirectoryAccessValidator
) : ViewModel() {

    private val disposables: CompositeDisposable = CompositeDisposable()

    private val _searchQuery = MutableStateFlow("")
    private val _sortingMode = MutableStateFlow(settingsRepository.getRomSortingMode())
    private val _sortingOrder = MutableStateFlow(settingsRepository.getRomSortingOrder())

    private val _hasSearchDirectories = SubjectSharedFlow<Boolean>()
    val hasSearchDirectories: Flow<Boolean> = _hasSearchDirectories

    private val _invalidDirectoryAccessEvent = EventSharedFlow<Unit>()
    val invalidDirectoryAccessEvent: Flow<Unit> = _invalidDirectoryAccessEvent

    private val _roms = MutableStateFlow<List<Rom>?>(null)
    val roms = _roms.asStateFlow()

    val onRomIconFilteringChanged = settingsRepository.observeRomIconFiltering()

    val romScanningStatus = romsRepository.getRomScanningStatus()

    init {
        settingsRepository.observeRomSearchDirectories()
            .startWith(settingsRepository.getRomSearchDirectories())
            .distinctUntilChanged()
            .subscribe { directories -> _hasSearchDirectories.tryEmit(directories.isNotEmpty()) }
            .addTo(disposables)

        combine(romsRepository.getRoms(), _searchQuery) { roms, query ->
            val romList = if (query.isEmpty()) {
                roms
            } else {
                withContext(Dispatchers.Default) {
                    roms.filter { rom ->
                        if (!isActive) {
                            return@withContext emptyList()
                        }

                        val normalizedName = Normalizer.normalize(rom.name, Normalizer.Form.NFD).replace("[^\\p{ASCII}]", "")
                        val normalizedPath = Normalizer.normalize(rom.fileName, Normalizer.Form.NFD).replace("[^\\p{ASCII}]", "")

                        normalizedName.contains(query, true) || normalizedPath.contains(query, true)
                    }
                }
            }

            _roms.value = when (_sortingMode.value) {
                SortingMode.ALPHABETICALLY -> romList.sortedWith(buildAlphabeticalRomComparator(_sortingOrder.value))
                SortingMode.RECENTLY_PLAYED -> romList.sortedWith(buildRecentlyPlayedRomComparator(_sortingOrder.value))
            }
        }.launchIn(viewModelScope)

        combine(_sortingMode, _sortingOrder) { sortingMode, sortingOrder ->
            _roms.value = when (sortingMode) {
                SortingMode.ALPHABETICALLY -> _roms.value?.sortedWith(buildAlphabeticalRomComparator(sortingOrder))
                SortingMode.RECENTLY_PLAYED -> _roms.value?.sortedWith(buildRecentlyPlayedRomComparator(sortingOrder))
            }
        }.launchIn(viewModelScope)
    }

    fun refreshRoms() {
        romsRepository.rescanRoms()
    }

    fun updateRomConfig(rom: Rom, newConfig: RomConfig) {
        newConfig.gbaCartPath?.let { uriPermissionManager.persistFilePermissions(it, Permission.READ) }
        newConfig.gbaSavePath?.let { uriPermissionManager.persistFilePermissions(it, Permission.READ_WRITE) }
        romsRepository.updateRomConfig(rom, newConfig)
    }

    fun getLayout(layoutId: UUID?): Single<LayoutConfiguration> {
        return if (layoutId == null) {
            Single.just(layoutsRepository.getGlobalLayoutPlaceholder())
        } else {
            layoutsRepository.getLayout(layoutId).toSingle(layoutsRepository.getGlobalLayoutPlaceholder())
        }
    }

    fun setRomLastPlayedNow(rom: Rom) {
        romsRepository.setRomLastPlayed(rom, Calendar.getInstance().time)
    }

    fun setRomSearchQuery(query: String?) {
        _searchQuery.tryEmit(Normalizer.normalize(query ?: "", Normalizer.Form.NFD).replace("[^\\p{ASCII}]", ""))
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

    fun getUriDocumentName(uri: Uri): String? {
        return uriHandler.getUriDocument(uri)?.name
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

    override fun onCleared() {
        super.onCleared()
        disposables.dispose()
    }
}