package me.magnum.melonds.ui.backgrounds

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import io.reactivex.disposables.CompositeDisposable
import me.magnum.melonds.common.Permission
import me.magnum.melonds.common.Schedulers
import me.magnum.melonds.common.UriPermissionManager
import me.magnum.melonds.domain.model.Background
import me.magnum.melonds.domain.model.Orientation
import me.magnum.melonds.domain.repositories.BackgroundRepository
import me.magnum.melonds.extensions.addTo
import java.util.*
import javax.inject.Inject

@HiltViewModel
class BackgroundsViewModel @Inject constructor(
        private val backgroundsRepository: BackgroundRepository,
        private val uriPermissionManager: UriPermissionManager,
        private val schedulers: Schedulers,
        savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val backgroundsLiveData = MutableLiveData<List<Background>>()
    private val currentSelectedBackground: MutableLiveData<UUID?>
    private val backgroundOrientationFilter: Orientation

    private val disposables = CompositeDisposable()

    init {
        val initialBackgroundId = savedStateHandle.get<String?>(BackgroundsActivity.KEY_INITIAL_BACKGROUND_ID)?.let { UUID.fromString(it) }
        currentSelectedBackground = MutableLiveData(initialBackgroundId)
        backgroundOrientationFilter = savedStateHandle.get<Int>(BackgroundsActivity.KEY_ORIENTATION_FILTER).let { Orientation.entries[it ?: throw NullPointerException()] }

        backgroundsRepository.getBackgrounds()
                .subscribeOn(schedulers.backgroundThreadScheduler)
                .subscribe { backgrounds ->
                    backgroundsLiveData.postValue(backgrounds.filter { it.orientation == backgroundOrientationFilter })
                }.addTo(disposables)
    }

    fun getSelectedBackgroundId(): LiveData<UUID?> {
        return currentSelectedBackground
    }

    fun getCurrentOrientationFilter(): Orientation {
        return backgroundOrientationFilter
    }

    fun getBackgrounds(): LiveData<List<Background>> {
        return backgroundsLiveData
    }

    fun addBackground(background: Background) {
        uriPermissionManager.persistFilePermissions(background.uri, Permission.READ)
        backgroundsRepository.addBackground(background)
    }

    fun deleteBackground(background: Background) {
        backgroundsRepository.deleteBackground(background)
                .subscribeOn(schedulers.backgroundThreadScheduler)
                .observeOn(schedulers.uiThreadScheduler)
                .subscribe {
                    if (background.id == currentSelectedBackground.value) {
                        // The selected background was deleted. Set current to None
                        currentSelectedBackground.value = null
                    }
                }
                .addTo(disposables)
    }

    override fun onCleared() {
        super.onCleared()
        disposables.clear()
    }
}