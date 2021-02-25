package me.magnum.melonds.ui.romlist

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import androidx.hilt.lifecycle.ViewModelInject
import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import io.reactivex.disposables.CompositeDisposable
import me.magnum.melonds.domain.model.*
import me.magnum.melonds.domain.repositories.RomsRepository
import me.magnum.melonds.domain.repositories.SettingsRepository
import me.magnum.melonds.extensions.addTo
import me.magnum.melonds.utils.ConfigurationUtils
import me.magnum.melonds.utils.RomIconProvider
import java.text.Normalizer
import java.util.*

class RomListViewModel @ViewModelInject constructor(
        private val context: Context,
        private val romsRepository: RomsRepository,
        private val settingsRepository: SettingsRepository,
        private val romIconProvider: RomIconProvider
) : ViewModel() {

    private val disposables: CompositeDisposable = CompositeDisposable()

    private val romsLiveData = MutableLiveData<List<Rom>>()
    private var searchDirectoriesLiveData: MutableLiveData<Array<Uri>>? = null
    private var romsFilteredLiveData: MediatorLiveData<List<Rom>>? = null

    private var romSearchQuery = ""
    private var sortingMode = settingsRepository.getRomSortingMode()
    private var sortingOrder = settingsRepository.getRomSortingOrder()

    init {
        settingsRepository.observeRomIconFiltering()
                .subscribe { romsLiveData.postValue(romsLiveData.value) }
                .addTo(disposables)
    }

    fun getRomScanningDirectories(): LiveData<Array<Uri>> {
        if (searchDirectoriesLiveData != null)
            return searchDirectoriesLiveData!!

        searchDirectoriesLiveData = MutableLiveData()
        settingsRepository.observeRomSearchDirectories()
                .startWith(settingsRepository.getRomSearchDirectories())
                .distinctUntilChanged()
                .subscribe { directories -> searchDirectoriesLiveData?.postValue(directories) }
                .addTo(disposables)

        return searchDirectoriesLiveData!!
    }

    fun getRoms(): LiveData<List<Rom>> {
        if (romsFilteredLiveData != null)
            return romsFilteredLiveData!!

        romsFilteredLiveData = MediatorLiveData<List<Rom>>().apply {
            addSource(romsLiveData) {
                val romList = if (romSearchQuery.isEmpty()) {
                    it
                } else {
                    it.filter { rom ->
                        val normalizedName = Normalizer.normalize(rom.name, Normalizer.Form.NFD).replace("[^\\p{ASCII}]", "")
                        val normalizedPath = Normalizer.normalize(DocumentFile.fromSingleUri(context, rom.uri)?.name, Normalizer.Form.NFD).replace("[^\\p{ASCII}]", "")

                        normalizedName.contains(romSearchQuery, true) || normalizedPath.contains(romSearchQuery, true)
                    }
                }

                value = when (sortingMode) {
                    SortingMode.ALPHABETICALLY -> romList.sortedWith(buildAlphabeticalRomComparator())
                    SortingMode.RECENTLY_PLAYED -> romList.sortedWith(buildRecentlyPlayedRomComparator())
                }
            }
        }

        romsRepository.getRoms()
                .subscribe { roms -> romsLiveData.postValue(roms) }
                .addTo(disposables)

        return romsFilteredLiveData!!
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
        romsRepository.updateRomConfig(rom, newConfig)
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

    fun getRomTargetConsoleType(rom: Rom): ConsoleType {
        return rom.config.runtimeConsoleType.targetConsoleType ?: settingsRepository.getDefaultConsoleType()
    }

    fun getDsConfigurationDirStatus(): ConfigurationUtils.ConfigurationDirStatus {
        return ConfigurationUtils.checkConfigurationDirectory(context, settingsRepository.getDsBiosDirectory(), ConsoleType.DS).status
    }

    fun getConsoleConfigurationDirStatus(consoleType: ConsoleType): ConfigurationUtils.ConfigurationDirStatus {
        val romTargetConfigurationDir = when(consoleType) {
            ConsoleType.DS -> settingsRepository.getDsBiosDirectory()
            ConsoleType.DSi -> settingsRepository.getDsiBiosDirectory()
        }
        return ConfigurationUtils.checkConfigurationDirectory(context, romTargetConfigurationDir, consoleType).status
    }

    fun getRomConfigurationDirStatus(rom: Rom): ConfigurationUtils.ConfigurationDirStatus {
        if (!settingsRepository.useCustomBios()) {
            return ConfigurationUtils.ConfigurationDirStatus.VALID
        }

        val romTargetConsoleType = getRomTargetConsoleType(rom)
        return getConsoleConfigurationDirStatus(romTargetConsoleType)
    }

    fun addRomSearchDirectory(directoryUri: Uri) {
        settingsRepository.addRomSearchDirectory(directoryUri)
    }

    fun setDsBiosDirectory(uri: Uri) {
        settingsRepository.setDsBiosDirectory(uri)
    }

    fun setDsiBiosDirectory(uri: Uri) {
        settingsRepository.setDsiBiosDirectory(uri)
    }

    fun getRomIcon(rom: Rom): RomIcon {
        val iconBitmap = romIconProvider.getRomIcon(rom)
        val iconFiltering = settingsRepository.getRomIconFiltering()
        return RomIcon(iconBitmap, iconFiltering)
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