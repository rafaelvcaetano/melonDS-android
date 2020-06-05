package me.magnum.melonds.ui.romlist

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import io.reactivex.disposables.CompositeDisposable
import me.magnum.melonds.model.Rom
import me.magnum.melonds.model.RomConfig
import me.magnum.melonds.model.RomScanningStatus
import me.magnum.melonds.repositories.RomsRepository

class RomListViewModel(private val romsRepository: RomsRepository) : ViewModel() {
    private val disposables: CompositeDisposable = CompositeDisposable()

    fun getRoms(): LiveData<List<Rom>> {
        val romsLiveData = MutableLiveData<List<Rom>>()
        val disposable = romsRepository.getRoms()
                .subscribe { roms -> romsLiveData.postValue(roms) }
        disposables.add(disposable)
        return romsLiveData
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

    override fun onCleared() {
        super.onCleared()
        disposables.dispose()
    }
}