package me.magnum.melonds.ui.romlist

import android.net.Uri
import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import io.reactivex.Single
import io.reactivex.disposables.CompositeDisposable
import me.magnum.melonds.common.DirectoryAccessValidator
import me.magnum.melonds.common.Permission
import me.magnum.melonds.common.Schedulers
import me.magnum.melonds.common.UriPermissionManager
import me.magnum.melonds.common.uridelegates.UriHandler
import me.magnum.melonds.domain.model.*
import me.magnum.melonds.domain.repositories.LayoutsRepository
import me.magnum.melonds.domain.repositories.RomsRepository
import me.magnum.melonds.domain.repositories.SettingsRepository
import me.magnum.melonds.domain.services.ConfigurationDirectoryVerifier
import me.magnum.melonds.extensions.addTo
import me.magnum.melonds.impl.RomIconProvider
import me.magnum.melonds.utils.SingleLiveEvent
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
        private val directoryAccessValidator: DirectoryAccessValidator,
        private val schedulers: Schedulers
) : ViewModel() {

    private val disposables: CompositeDisposable = CompositeDisposable()

    private val _invalidDirectoryAccessEvent = SingleLiveEvent<Unit>()
    val invalidDirectoryAccessEvent: LiveData<Unit> = _invalidDirectoryAccessEvent

    private val romsLiveData = MutableLiveData<List<Rom>>()
    private val hasSearchDirectoriesLiveData = MutableLiveData<Boolean>()
    private val romsFilteredLiveData: MediatorLiveData<List<Rom>>

    private var romSearchQuery = ""
    private var sortingMode = settingsRepository.getRomSortingMode()
    private var sortingOrder = settingsRepository.getRomSortingOrder()

    init {
        settingsRepository.observeRomIconFiltering()
                .subscribe { romsLiveData.postValue(romsLiveData.value) }
                .addTo(disposables)

        settingsRepository.observeRomSearchDirectories()
            .startWith(settingsRepository.getRomSearchDirectories())
            .distinctUntilChanged()
            .subscribe { directories -> hasSearchDirectoriesLiveData.postValue(directories.isNotEmpty()) }
            .addTo(disposables)

        romsFilteredLiveData = MediatorLiveData<List<Rom>>().apply {
            addSource(romsLiveData) {
                val romList = if (romSearchQuery.isEmpty()) {
                    it
                } else {
                    it?.filter { rom ->
                        val normalizedName = Normalizer.normalize(rom.name, Normalizer.Form.NFD).replace("[^\\p{ASCII}]", "")
                        val normalizedPath = Normalizer.normalize(uriHandler.getUriDocument(rom.uri)?.name, Normalizer.Form.NFD).replace("[^\\p{ASCII}]", "")

                        normalizedName.contains(romSearchQuery, true) || normalizedPath.contains(romSearchQuery, true)
                    }
                }

                if (romList != null) {
                    value = when (sortingMode) {
                        SortingMode.ALPHABETICALLY -> romList.sortedWith(buildAlphabeticalRomComparator())
                        SortingMode.RECENTLY_PLAYED -> romList.sortedWith(buildRecentlyPlayedRomComparator())
                    }
                }
            }
        }

        romsRepository.getRoms()
            .subscribeOn(schedulers.backgroundThreadScheduler)
            .subscribe { roms -> romsLiveData.postValue(roms) }
            .addTo(disposables)
    }

    fun hasRomScanningDirectories(): LiveData<Boolean> {
        return hasSearchDirectoriesLiveData
    }

    fun getRoms(): LiveData<List<Rom>> {
        return romsFilteredLiveData
    }

    fun getRomScanningStatus(): LiveData<RomScanningStatus> {
        val scanningStatusLiveData = MutableLiveData<RomScanningStatus>()
        val disposable = romsRepository.getRomScanningStatus()
                .subscribe { status -> scanningStatusLiveData.postValue(status) }
        disposables.add(disposable)
        return scanningStatusLiveData
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
        romSearchQuery = Normalizer.normalize(query ?: "", Normalizer.Form.NFD).replace("[^\\p{ASCII}]", "")
        romsLiveData.value = romsLiveData.value
    }

    fun setRomSorting(sortingMode: SortingMode) {
        if (sortingMode == this.sortingMode) {
            sortingOrder = if (sortingOrder == SortingOrder.ASCENDING)
                SortingOrder.DESCENDING
            else
                SortingOrder.ASCENDING

            settingsRepository.setRomSortingOrder(sortingOrder)
        } else {
            this.sortingMode = sortingMode
            sortingOrder = sortingMode.defaultOrder

            settingsRepository.setRomSortingMode(sortingMode)
            settingsRepository.setRomSortingOrder(sortingOrder)
        }

        romsLiveData.value = romsLiveData.value
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
            _invalidDirectoryAccessEvent.call()
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
            _invalidDirectoryAccessEvent.call()
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
            _invalidDirectoryAccessEvent.call()
            false
        }
    }

    fun getRomIcon(rom: Rom): Single<RomIcon> {
        return romIconProvider.getRomIcon(rom)
            .subscribeOn(schedulers.backgroundThreadScheduler)
            .observeOn(schedulers.uiThreadScheduler)
            .materialize()
            .map {
                val bitmap = it.value
                val iconFiltering = settingsRepository.getRomIconFiltering()
                RomIcon(bitmap, iconFiltering)
            }
    }

    private fun buildAlphabeticalRomComparator(): Comparator<Rom> {
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

    private fun buildRecentlyPlayedRomComparator(): Comparator<Rom> {
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