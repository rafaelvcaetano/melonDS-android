package me.magnum.melonds.ui.romlist

import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import io.reactivex.disposables.CompositeDisposable
import me.magnum.melonds.extensions.addTo
import me.magnum.melonds.model.Rom
import me.magnum.melonds.model.RomConfig
import me.magnum.melonds.model.RomScanningStatus
import me.magnum.melonds.repositories.RomsRepository
import java.text.Normalizer

class RomListViewModel(private val romsRepository: RomsRepository) : ViewModel() {
    private val disposables: CompositeDisposable = CompositeDisposable()

    private val romsLiveData = MutableLiveData<List<Rom>>()
    private var romsFilteredLiveData: MediatorLiveData<List<Rom>>? = null

    private var romSearchQuery = ""

    fun getRoms(): LiveData<List<Rom>> {
        if (romsFilteredLiveData != null)
            return romsFilteredLiveData!!

        romsFilteredLiveData = MediatorLiveData<List<Rom>>().apply {
            addSource(romsLiveData) {
                if (romSearchQuery.isEmpty()) {
                    value = it
                    return@addSource
                }

                val filteredRoms = it.filter { rom ->
                    val normalizedName = Normalizer.normalize(rom.name, Normalizer.Form.NFD).replace("[^\\p{ASCII}]", "")
                    val normalizedPath = Normalizer.normalize(rom.path, Normalizer.Form.NFD).replace("[^\\p{ASCII}]", "")

                    normalizedName.contains(romSearchQuery, true) || normalizedPath.contains(romSearchQuery, true)
                }
                value = filteredRoms
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

    fun setRomSearchQuery(query: String?) {
        romSearchQuery = Normalizer.normalize(query ?: "", Normalizer.Form.NFD).replace("[^\\p{ASCII}]", "")
        romsLiveData.value = romsLiveData.value
    }

    override fun onCleared() {
        super.onCleared()
        disposables.dispose()
    }
}