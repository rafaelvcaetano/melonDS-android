package me.magnum.melonds.ui.layouteditor

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import io.reactivex.Maybe
import io.reactivex.disposables.CompositeDisposable
import me.magnum.melonds.common.Schedulers
import me.magnum.melonds.domain.model.*
import me.magnum.melonds.domain.repositories.BackgroundRepository
import me.magnum.melonds.domain.repositories.LayoutsRepository
import me.magnum.melonds.extensions.addTo
import me.magnum.melonds.impl.DefaultLayoutProvider
import me.magnum.melonds.utils.SingleLiveEvent
import java.util.*
import javax.inject.Inject

@HiltViewModel
class LayoutEditorViewModel @Inject constructor(
        private val layoutsRepository: LayoutsRepository,
        private val backgroundsRepository: BackgroundRepository,
        private val defaultLayoutProvider: DefaultLayoutProvider,
        private val schedulers: Schedulers,
        savedStateHandle: SavedStateHandle
) : ViewModel() {

    private var currentSystemOrientation: Orientation? = null
    private var currentLayoutConfiguration: LayoutConfiguration? = null
    private var initialLayoutConfiguration: LayoutConfiguration? = null
    private val backgroundLiveData = MutableLiveData<RuntimeBackground>()
    private val layoutPropertiesUpdateEvent = SingleLiveEvent<Unit>()
    private val disposables = CompositeDisposable()

    init {
        val layoutId = savedStateHandle.get<String?>(LayoutEditorActivity.KEY_LAYOUT_ID)?.let { UUID.fromString(it) }
        if (layoutId != null) {
            // Good? No. Does it work? Hell yeah!
            val layout = layoutsRepository.getLayout(layoutId).blockingGet()
            initialLayoutConfiguration = layout
            currentLayoutConfiguration = layout
        }
    }

    fun setCurrentSystemOrientation(orientation: Orientation) {
        currentSystemOrientation = orientation
        loadBackgroundForCurrentLayoutConfiguration()
    }

    fun getCurrentLayoutConfiguration(): LayoutConfiguration? {
        return currentLayoutConfiguration
    }

    fun setCurrentLayoutConfiguration(layoutConfiguration: LayoutConfiguration) {
        currentLayoutConfiguration = layoutConfiguration
        initialLayoutConfiguration = layoutConfiguration.copy()
        loadBackgroundForCurrentLayoutConfiguration()
    }

    fun getBackground(): LiveData<RuntimeBackground> {
        return backgroundLiveData
    }

    fun onLayoutPropertiesUpdate(): LiveData<Unit> {
        return layoutPropertiesUpdateEvent
    }

    fun getBackgroundName(backgroundId: UUID): Maybe<String> {
        return backgroundsRepository.getBackground(backgroundId).map { it.name }
    }

    private fun loadBackgroundForCurrentLayoutConfiguration() {
        val layoutConfiguration = currentLayoutConfiguration ?: return
        currentSystemOrientation?.let { systemOrientation ->
            when (systemOrientation) {
                Orientation.PORTRAIT -> loadBackground(layoutConfiguration.portraitLayout.backgroundId, layoutConfiguration.portraitLayout.backgroundMode)
                Orientation.LANDSCAPE -> loadBackground(layoutConfiguration.landscapeLayout.backgroundId, layoutConfiguration.landscapeLayout.backgroundMode)
            }
        }
    }

    private fun loadBackground(backgroundId: UUID?, mode: BackgroundMode) {
        if (backgroundId == null) {
            backgroundLiveData.value = RuntimeBackground(null, mode)
        } else {
            backgroundsRepository.getBackground(backgroundId)
                    .subscribeOn(schedulers.backgroundThreadScheduler)
                    .materialize()
                    .subscribe { message ->
                        backgroundLiveData.postValue(RuntimeBackground(message.value, mode))
                    }
                    .addTo(disposables)
        }
    }

    fun setCurrentLayoutName(name: String) {
        currentLayoutConfiguration = currentLayoutConfiguration?.copy(
                name = name
        )
    }

    fun saveCurrentLayout() {
        currentLayoutConfiguration?.let {
            layoutsRepository.saveLayout(it)
        }
    }

    fun getInitialLayoutConfiguration(): LayoutConfiguration? {
        return initialLayoutConfiguration
    }

    fun saveLayoutToCurrentConfiguration(layout: UILayout) {
        currentLayoutConfiguration?.let {
            val layoutOrientation = currentSystemOrientation ?: return
            currentLayoutConfiguration = when (layoutOrientation) {
                Orientation.PORTRAIT -> it.copy(portraitLayout = it.portraitLayout.copy(components = layout.components))
                Orientation.LANDSCAPE -> it.copy(landscapeLayout = it.landscapeLayout.copy(components = layout.components))
            }
        }
    }

    fun savePropertiesToCurrentConfiguration(name: String?, orientation: LayoutConfiguration.LayoutOrientation, useCustomOpacity: Boolean, layoutOpacity: Int) {
        currentLayoutConfiguration?.let {
            val hasLayoutOrientationChanged = it.orientation != orientation

            currentLayoutConfiguration = it.copy(
                    name = name,
                    orientation = orientation,
                    useCustomOpacity = useCustomOpacity,
                    opacity = layoutOpacity
            )

            if (hasLayoutOrientationChanged) {
                loadBackgroundForCurrentLayoutConfiguration()
            }

            layoutPropertiesUpdateEvent.call()
        }
    }

    fun saveBackgroundsToCurrentConfiguration(portraitBackground: UUID?, portraitBackgroundMode: BackgroundMode, landscapeBackground: UUID?, landscapeBackgroundMode: BackgroundMode) {
        currentLayoutConfiguration?.let {
            currentLayoutConfiguration = it.copy(
                    portraitLayout = it.portraitLayout.copy(
                            backgroundId = portraitBackground,
                            backgroundMode = portraitBackgroundMode
                    ),
                    landscapeLayout = it.landscapeLayout.copy(
                            backgroundId = landscapeBackground,
                            backgroundMode = landscapeBackgroundMode
                    )
            )

            loadBackgroundForCurrentLayoutConfiguration()
        }
    }

    fun getDefaultLayoutConfiguration(): LayoutConfiguration {
        return defaultLayoutProvider.defaultLayout
    }

    fun isCurrentLayoutNew(): Boolean {
        return currentLayoutConfiguration?.id == null
    }

    override fun onCleared() {
        super.onCleared()
        disposables.clear()
    }
}