package me.magnum.melonds.ui.layouteditor

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import me.magnum.melonds.domain.model.RuntimeBackground
import me.magnum.melonds.domain.model.layout.BackgroundMode
import me.magnum.melonds.domain.model.layout.LayoutConfiguration
import me.magnum.melonds.domain.model.layout.LayoutConfiguration.LayoutOrientation
import me.magnum.melonds.domain.model.layout.PositionedLayoutComponent
import me.magnum.melonds.domain.model.layout.ScreenFold
import me.magnum.melonds.domain.model.layout.UILayout
import me.magnum.melonds.domain.model.layout.UILayoutVariant
import me.magnum.melonds.domain.model.ui.Orientation
import me.magnum.melonds.domain.repositories.BackgroundRepository
import me.magnum.melonds.domain.repositories.LayoutsRepository
import me.magnum.melonds.impl.layout.UILayoutProvider
import me.magnum.melonds.ui.layouteditor.model.CurrentLayoutState
import me.magnum.melonds.ui.layouteditor.model.LayoutBackgroundProperties
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class LayoutEditorViewModel @Inject constructor(
    private val layoutsRepository: LayoutsRepository,
    private val backgroundsRepository: BackgroundRepository,
    private val uiLayoutProvider: UILayoutProvider,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val _currentLayoutConfiguration = MutableStateFlow<LayoutConfiguration?>(null)

    private var initialLayoutConfiguration: LayoutConfiguration? = null
    private var currentLayoutVariant: UILayoutVariant? = null

    private val _background = MutableStateFlow<RuntimeBackground?>(null)
    val background = _background.asStateFlow()

    private val _currentLayout = MutableStateFlow<CurrentLayoutState?>(null)
    val currentLayout = _currentLayout.asStateFlow()

    private val _layoutBackgroundProperties = MutableStateFlow<LayoutBackgroundProperties?>(null)
    val layoutBackgroundProperties by lazy {
        viewModelScope.launch {
            currentLayout.filterNotNull().map {
                LayoutBackgroundProperties(it.layout.backgroundId, it.layout.backgroundMode)
            }.collect(_layoutBackgroundProperties)
        }
        _layoutBackgroundProperties.asStateFlow()
    }

    init {
        val layoutId = savedStateHandle.get<String?>(LayoutEditorActivity.KEY_LAYOUT_ID)?.let { UUID.fromString(it) }
        if (layoutId != null) {
            viewModelScope.launch {
                val initialLayout = layoutsRepository.getLayout(layoutId)
                initialLayoutConfiguration = initialLayout
                _currentLayoutConfiguration.value = initialLayout
            }
        } else {
            val newLayout = LayoutConfiguration.newCustom()
            initialLayoutConfiguration = newLayout
            _currentLayoutConfiguration.value = newLayout
        }

        viewModelScope.launch {
            _currentLayoutConfiguration.filterNotNull().collect {
                uiLayoutProvider.setCurrentLayoutConfiguration(it)
            }
        }

        viewModelScope.launch {
            uiLayoutProvider.currentLayout.collect {
                if (it == null) {
                    _currentLayout.value = null
                } else {
                    val (variant, layout) = it
                    val currentLayoutConfig = _currentLayoutConfiguration.value ?: return@collect
                    currentLayoutVariant = variant
                    _currentLayout.value = CurrentLayoutState(layout, currentLayoutConfig.orientation)
                }
            }
        }
        viewModelScope.launch {
            _currentLayout.distinctUntilChangedBy { it?.layout?.backgroundId to it?.layout?.backgroundMode }.collect {
                if (it != null) {
                    loadBackground(it.layout.backgroundId, it.layout.backgroundMode)
                } else {
                    // Unload the background. The background mode doesn't matter
                    loadBackground(null, BackgroundMode.FIT_CENTER)
                }
            }
        }
    }

    fun setCurrentSystemOrientation(orientation: Orientation) {
        uiLayoutProvider.updateCurrentOrientation(orientation)
    }

    fun setCurrentUiSize(width: Int, height: Int) {
        uiLayoutProvider.updateUiSize(width, height)
    }

    fun setScreenFolds(folds: List<ScreenFold>) {
        uiLayoutProvider.updateFolds(folds)
    }

    fun getCurrentLayoutConfiguration(): LayoutConfiguration? {
        return _currentLayoutConfiguration.value
    }

    suspend fun getBackgroundName(backgroundId: UUID): String? {
        return backgroundsRepository.getBackground(backgroundId)?.name
    }

    private fun loadBackground(backgroundId: UUID?, mode: BackgroundMode) {
        if (backgroundId == null) {
            _background.value = RuntimeBackground(null, mode)
        } else {
            viewModelScope.launch {
                val background = backgroundsRepository.getBackground(backgroundId)
                _background.value = RuntimeBackground(background, mode)
            }
        }
    }

    fun setCurrentLayoutName(name: String) {
        _currentLayoutConfiguration.update {
            it?.copy(
                name = name
            )
        }
    }

    fun saveCurrentLayout() {
        _currentLayoutConfiguration.value?.let {
            viewModelScope.launch {
                layoutsRepository.saveLayout(it)
            }
        }
    }

    fun revertLayoutChanges() {
        _currentLayoutConfiguration.value = initialLayoutConfiguration
    }

    fun resetLayout() {
        _currentLayoutConfiguration.update {
            it?.copy(
                orientation = LayoutOrientation.FOLLOW_SYSTEM,
                useCustomOpacity = false,
                opacity = 50,
                layoutVariants = emptyMap(),
            )
        }
    }

    fun saveLayoutToCurrentConfiguration(layoutComponents: List<PositionedLayoutComponent>) {
        val currentVariant = currentLayoutVariant ?: return

        _currentLayoutConfiguration.update {
            it?.copy(
                layoutVariants = it.layoutVariants.toMutableMap().apply {
                    val updatedLayout = this[currentVariant]?.copy(components = layoutComponents) ?: UILayout(layoutComponents)
                    this[currentVariant] = updatedLayout
                }
            )
        }
    }

    fun savePropertiesToCurrentConfiguration(name: String?, orientation: LayoutOrientation, useCustomOpacity: Boolean, layoutOpacity: Int) {
        _currentLayoutConfiguration.value?.let {
            _currentLayoutConfiguration.value = it.copy(
                name = name,
                orientation = orientation,
                useCustomOpacity = useCustomOpacity,
                opacity = layoutOpacity
            )
        }
    }

    fun setBackgroundPropertiesBackgroundId(backgroundId: UUID?) {
        _layoutBackgroundProperties.update {
            it?.copy(backgroundId = backgroundId)
        }
    }

    fun setBackgroundPropertiesBackgroundMode(backgroundMode: BackgroundMode) {
        _layoutBackgroundProperties.update {
            it?.copy(backgroundMode = backgroundMode)
        }
    }

    fun saveBackgroundToCurrentConfiguration() {
        val currentVariant = currentLayoutVariant ?: return
        val currentBackgroundProperties = _layoutBackgroundProperties.value ?: return

        _currentLayoutConfiguration.update { layoutConfiguration ->
            layoutConfiguration?.copy(
                layoutVariants = layoutConfiguration.layoutVariants.toMutableMap().apply {
                    val updatedLayout = if (containsKey(currentVariant)) {
                        this[currentVariant]?.copy(backgroundId = currentBackgroundProperties.backgroundId, backgroundMode = currentBackgroundProperties.backgroundMode)
                    } else {
                        UILayout(
                            backgroundId = currentBackgroundProperties.backgroundId,
                            backgroundMode = currentBackgroundProperties.backgroundMode,
                            components = null,
                        )
                    }
                    updatedLayout?.let {
                        this[currentVariant] = it
                    }
                }
            )
        }
    }

    fun resetBackgroundProperties() {
        val currentLayout = currentLayout.value?.layout
        _layoutBackgroundProperties.value = currentLayout?.let {
            LayoutBackgroundProperties(currentLayout.backgroundId, currentLayout.backgroundMode)
        }
    }

    fun currentLayoutHasName(): Boolean {
        return !_currentLayoutConfiguration.value?.name.isNullOrEmpty()
    }
}