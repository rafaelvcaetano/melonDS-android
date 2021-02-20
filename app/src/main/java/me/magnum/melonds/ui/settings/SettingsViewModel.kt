package me.magnum.melonds.ui.settings

import android.net.Uri
import androidx.hilt.lifecycle.ViewModelInject
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import io.reactivex.disposables.CompositeDisposable
import me.magnum.melonds.domain.model.CheatImportProgress
import me.magnum.melonds.domain.repositories.CheatsRepository
import me.magnum.melonds.extensions.addTo
import me.magnum.melonds.impl.NdsRomCache

class SettingsViewModel @ViewModelInject constructor(private val cheatsRepository: CheatsRepository, private val romCache: NdsRomCache) : ViewModel() {
    private val disposables = CompositeDisposable()

    fun importCheatsDatabase(databaseUri: Uri) {
        cheatsRepository.importCheats(databaseUri)
    }

    fun areCheatsBeingImported(): Boolean {
        return cheatsRepository.isCheatImportOngoing()
    }

    fun getRomCacheSize(): LiveData<Long> {
        val liveData = MutableLiveData<Long>()
        romCache.getCacheSize().subscribe { size ->
            liveData.postValue(size)
        }.addTo(disposables)

        return liveData
    }

    fun clearRomCache(): Boolean {
        return romCache.clearCache()
    }

    fun observeCheatsImportProgress(): LiveData<CheatImportProgress> {
        val liveData = MutableLiveData<CheatImportProgress>()

        cheatsRepository.getCheatImportProgress().subscribe {
            liveData.postValue(it)
        }.addTo(disposables)

        return liveData
    }

    override fun onCleared() {
        super.onCleared()
        disposables.clear()
    }
}