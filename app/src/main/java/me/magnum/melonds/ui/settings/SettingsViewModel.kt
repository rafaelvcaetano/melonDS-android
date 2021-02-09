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

class SettingsViewModel @ViewModelInject constructor(private val cheatsRepository: CheatsRepository) : ViewModel() {
    private val disposables = CompositeDisposable()

    fun importCheatsDatabase(databaseUri: Uri) {
        cheatsRepository.importCheats(databaseUri)
    }

    fun areCheatsBeingImported(): Boolean {
        return cheatsRepository.isCheatImportOngoing()
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