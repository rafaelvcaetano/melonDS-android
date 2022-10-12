package me.magnum.melonds.ui.romlist

import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import io.reactivex.disposables.CompositeDisposable
import me.magnum.melonds.common.Schedulers
import me.magnum.melonds.domain.model.AppUpdate
import me.magnum.melonds.domain.model.DownloadProgress
import me.magnum.melonds.domain.repositories.UpdatesRepository
import me.magnum.melonds.domain.services.UpdateInstallManager
import me.magnum.melonds.extensions.addTo
import me.magnum.melonds.utils.SingleLiveEvent
import javax.inject.Inject

@HiltViewModel
class UpdatesViewModel @Inject constructor(
    private val updatesRepository: UpdatesRepository,
    private val updateInstallManager: UpdateInstallManager,
    private val schedulers: Schedulers
) : ViewModel() {

    private val disposables = CompositeDisposable()
    private val appUpdateLiveData = SingleLiveEvent<AppUpdate>()
    private val updateDownloadProgressLiveData = SingleLiveEvent<DownloadProgress>()

    init {
        updatesRepository.checkNewUpdate()
            .onErrorComplete()
            .subscribeOn(schedulers.backgroundThreadScheduler)
            .subscribe {
                appUpdateLiveData.postValue(it)
            }
            .addTo(disposables)
    }

    fun getAppUpdate(): LiveData<AppUpdate> {
        return appUpdateLiveData
    }

    fun getDownloadProgress(): LiveData<DownloadProgress> {
        return updateDownloadProgressLiveData
    }

    fun downloadUpdate(update: AppUpdate) {
        updateInstallManager.downloadAndInstallUpdate(update)
            .subscribeOn(schedulers.backgroundThreadScheduler)
            .subscribe {
                updateDownloadProgressLiveData.postValue(it)
            }
            .addTo(disposables)
    }

    fun skipUpdate(update: AppUpdate) {
        updatesRepository.skipUpdate(update)
    }

    override fun onCleared() {
        super.onCleared()
        disposables.clear()
    }
}