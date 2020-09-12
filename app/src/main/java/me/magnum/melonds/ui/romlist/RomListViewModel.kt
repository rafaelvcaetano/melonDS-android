package me.magnum.melonds.ui.romlist

import android.net.Uri
import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import io.reactivex.disposables.CompositeDisposable
import me.magnum.melonds.extensions.addTo
import me.magnum.melonds.model.*
import me.magnum.melonds.repositories.RomsRepository
import me.magnum.melonds.repositories.SettingsRepository
import java.text.Normalizer
import java.util.*

class RomListViewModel(private val romsRepository: RomsRepository, private val settingsRepository: SettingsRepository) : ViewModel() {
    private val disposables: CompositeDisposable = CompositeDisposable()

    private val romsLiveData = MutableLiveData<List<Rom>>()
    private var searchDirectoriesLiveData: MutableLiveData<Array<Uri>>? = null
    private var romsFilteredLiveData: MediatorLiveData<List<Rom>>? = null

    private var romSearchQuery = ""
    private var sortingMode = settingsRepository.getRomSortingMode()
    private var sortingOrder = settingsRepository.getRomSortingOrder()

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
                        val normalizedPath = Normalizer.normalize(rom.path, Normalizer.Form.NFD).replace("[^\\p{ASCII}]", "")

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

    fun addRomSearchDirectory(directoryUri: Uri) {
        settingsRepository.addRomSearchDirectory(directoryUri)
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