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
import me.magnum.melonds.domain.model.layout.LayoutDisplay
import me.magnum.melonds.domain.model.layout.LayoutDisplayPair
import me.magnum.melonds.domain.model.layout.PositionedLayoutComponent
import me.magnum.melonds.domain.model.layout.ScreenFold
import me.magnum.melonds.domain.model.layout.ScreenLayout
import me.magnum.melonds.domain.model.layout.UILayout
import me.magnum.melonds.domain.model.layout.UILayoutVariant
import me.magnum.melonds.domain.model.ui.Orientation
import me.magnum.melonds.domain.repositories.BackgroundRepository
import me.magnum.melonds.domain.repositories.LayoutsRepository
import me.magnum.melonds.impl.layout.UILayoutProvider
import me.magnum.melonds.ui.layouteditor.model.CurrentLayoutState
import me.magnum.melonds.ui.layouteditor.model.LayoutBackgroundProperties
import me.magnum.melonds.ui.layouteditor.model.LayoutTarget
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class LayoutEditorViewModel @Inject constructor(
    private val layoutsRepository: LayoutsRepository,
    private val backgroundsRepository: BackgroundRepository,
    private val uiLayoutProvider: UILayoutProvider,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private var initialLayoutConfiguration: LayoutConfiguration? = null
    private var currentLayoutVariant: UILayoutVariant? = null

    private val _currentLayoutConfiguration = MutableStateFlow<LayoutConfiguration?>(null)
    val currentLayoutConfiguration = _currentLayoutConfiguration.asStateFlow()

    private val _mainScreenBackground = MutableStateFlow<RuntimeBackground?>(null)
    val mainScreenBackground = _mainScreenBackground.asStateFlow()

    private val _secondaryScreenBackground = MutableStateFlow<RuntimeBackground?>(null)
    val secondaryScreenBackground = _secondaryScreenBackground.asStateFlow()

    private val _currentLayout = MutableStateFlow<CurrentLayoutState?>(null)
    val currentLayout = _currentLayout.asStateFlow()

    private val _mainScreenBackgroundProperties = MutableStateFlow<LayoutBackgroundProperties?>(null)
    val mainScreenBackgroundProperties by lazy {
        viewModelScope.launch {
            currentLayout.filterNotNull().map {
                LayoutBackgroundProperties(it.layout.mainScreenLayout.backgroundId, it.layout.mainScreenLayout.backgroundMode)
            }.collect(_mainScreenBackgroundProperties)
        }
        _mainScreenBackgroundProperties.asStateFlow()
    }

    private val _secondaryScreenBackgroundProperties = MutableStateFlow<LayoutBackgroundProperties?>(null)
    val secondaryScreenBackgroundProperties by lazy {
        viewModelScope.launch {
            currentLayout.filterNotNull().map {
                LayoutBackgroundProperties(it.layout.secondaryScreenLayout.backgroundId, it.layout.secondaryScreenLayout.backgroundMode)
            }.collect(_secondaryScreenBackgroundProperties)
        }
        _secondaryScreenBackgroundProperties.asStateFlow()
    }

    init {
        val isExternal = savedStateHandle.get<Boolean>(LayoutEditorActivity.KEY_IS_EXTERNAL) ?: false
        val layoutId = savedStateHandle.get<String?>(LayoutEditorActivity.KEY_LAYOUT_ID)?.let { UUID.fromString(it) }
        if (layoutId != null) {
            viewModelScope.launch {
                val initialLayout = layoutsRepository.getLayout(layoutId)
                initialLayoutConfiguration = initialLayout
                _currentLayoutConfiguration.value = initialLayout
            }
        } else {
            val newLayout = LayoutConfiguration.newCustom(
            )
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
            _currentLayout.distinctUntilChangedBy { it?.layout?.mainScreenLayout?.backgroundId to it?.layout?.mainScreenLayout?.backgroundMode }.collect {
                if (it != null) {
                    loadMainScreenBackgroundBackground(it.layout.mainScreenLayout.backgroundId, it.layout.mainScreenLayout.backgroundMode)
                } else {
                    // Unload the background. The background mode doesn't matter
                    loadMainScreenBackgroundBackground(null, BackgroundMode.FIT_CENTER)
                }
            }
        }
        viewModelScope.launch {
            _currentLayout.distinctUntilChangedBy { it?.layout?.secondaryScreenLayout?.backgroundId to it?.layout?.secondaryScreenLayout?.backgroundMode }.collect {
                if (it != null) {
                    loadSecondaryScreenBackgroundBackground(it.layout.secondaryScreenLayout.backgroundId, it.layout.secondaryScreenLayout.backgroundMode)
                } else {
                    // Unload the background. The background mode doesn't matter
                    loadSecondaryScreenBackgroundBackground(null, BackgroundMode.FIT_CENTER)
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

    fun setConnectedDisplays(displays: LayoutDisplayPair) {
        uiLayoutProvider.updateDisplays(displays)
    }

    suspend fun getBackgroundName(backgroundId: UUID): String? {
        return backgroundsRepository.getBackground(backgroundId)?.name
    }

    private fun loadMainScreenBackgroundBackground(backgroundId: UUID?, mode: BackgroundMode) {
        if (backgroundId == null) {
            _mainScreenBackground.value = RuntimeBackground(null, mode)
        } else {
            viewModelScope.launch {
                val background = backgroundsRepository.getBackground(backgroundId)
                _mainScreenBackground.value = RuntimeBackground(background, mode)
            }
        }
    }

    private fun loadSecondaryScreenBackgroundBackground(backgroundId: UUID?, mode: BackgroundMode) {
        if (backgroundId == null) {
            _secondaryScreenBackground.value = RuntimeBackground(null, mode)
        } else {
            viewModelScope.launch {
                val background = backgroundsRepository.getBackground(backgroundId)
                _secondaryScreenBackground.value = RuntimeBackground(background, mode)
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

    fun saveLayoutToCurrentConfiguration(
        layoutComponents: List<PositionedLayoutComponent>?,
        secondaryDisplayLayoutComponents: List<PositionedLayoutComponent>?,
    ) {
        if (layoutComponents == null && secondaryDisplayLayoutComponents == null) return
        val currentVariant = currentLayoutVariant ?: return

        _currentLayoutConfiguration.update {
            it?.copy(
                layoutVariants = it.layoutVariants.toMutableMap().apply {
                    val currentLayout = this[currentVariant]
                    val updatedLayout = if (currentLayout != null) {
                        val mainScreenLayout = layoutComponents?.let {
                            currentLayout.mainScreenLayout.copy(components = it)
                        } ?: currentLayout.mainScreenLayout
                        val secondaryScreenLayout = secondaryDisplayLayoutComponents?.let {
                            currentLayout.secondaryScreenLayout.copy(components = it)
                        } ?: currentLayout.secondaryScreenLayout

                        currentLayout.copy(
                            mainScreenLayout = mainScreenLayout,
                            secondaryScreenLayout = secondaryScreenLayout,
                        )
                    } else {
                        UILayout(ScreenLayout(layoutComponents), ScreenLayout(secondaryDisplayLayoutComponents))
                    }

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

    fun setBackgroundPropertiesBackgroundId(target: LayoutTarget, backgroundId: UUID?) {
        when (target) {
            LayoutTarget.MAIN_SCREEN -> _mainScreenBackgroundProperties.update {
                it?.copy(backgroundId = backgroundId)
            }
            LayoutTarget.SECONDARY_SCREEN -> _secondaryScreenBackgroundProperties.update {
                it?.copy(backgroundId = backgroundId)
            }
        }
    }

    fun setBackgroundPropertiesBackgroundMode(target: LayoutTarget, backgroundMode: BackgroundMode) {
        when (target) {
            LayoutTarget.MAIN_SCREEN -> _mainScreenBackgroundProperties.update {
                it?.copy(backgroundMode = backgroundMode)
            }
            LayoutTarget.SECONDARY_SCREEN -> _secondaryScreenBackgroundProperties.update {
                it?.copy(backgroundMode = backgroundMode)
            }
        }
    }

    fun saveBackgroundToCurrentConfiguration(target: LayoutTarget) {
        val currentVariant = currentLayoutVariant ?: return
        val currentBackgroundProperties = when (target) {
            LayoutTarget.MAIN_SCREEN -> _mainScreenBackgroundProperties.value
            LayoutTarget.SECONDARY_SCREEN -> _secondaryScreenBackgroundProperties.value
        } ?: return

        _currentLayoutConfiguration.update { layoutConfiguration ->
            layoutConfiguration?.copy(
                layoutVariants = layoutConfiguration.layoutVariants.toMutableMap().apply {
                    val updatedLayout = when (target) {
                        LayoutTarget.MAIN_SCREEN -> {
                            if (containsKey(currentVariant)) {
                                this[currentVariant]?.mainScreenLayout?.copy(
                                    backgroundId = currentBackgroundProperties.backgroundId,
                                    backgroundMode = currentBackgroundProperties.backgroundMode,
                                )?.let { updatedScreenLayout ->
                                    this[currentVariant]?.copy(mainScreenLayout = updatedScreenLayout)
                                }
                            } else {
                                UILayout(
                                    mainScreenLayout = ScreenLayout(
                                        backgroundId = currentBackgroundProperties.backgroundId,
                                        backgroundMode = currentBackgroundProperties.backgroundMode,
                                        components = null,
                                    ),
                                    secondaryScreenLayout = ScreenLayout(),
                                )
                            }
                        }
                        LayoutTarget.SECONDARY_SCREEN -> {
                            if (containsKey(currentVariant)) {
                                this[currentVariant]?.secondaryScreenLayout?.copy(
                                    backgroundId = currentBackgroundProperties.backgroundId,
                                    backgroundMode = currentBackgroundProperties.backgroundMode,
                                )?.let { updatedScreenLayout ->
                                    this[currentVariant]?.copy(secondaryScreenLayout = updatedScreenLayout)
                                }
                            } else {
                                UILayout(
                                    mainScreenLayout = ScreenLayout(),
                                    secondaryScreenLayout = ScreenLayout(
                                        backgroundId = currentBackgroundProperties.backgroundId,
                                        backgroundMode = currentBackgroundProperties.backgroundMode,
                                        components = null,
                                    ),
                                )
                            }
                        }
                    }
                    updatedLayout?.let {
                        this[currentVariant] = it
                    }
                }
            )
        }
    }

    fun resetBackgroundProperties(target: LayoutTarget) {
        val currentLayout = currentLayout.value?.layout
        when (target) {
            LayoutTarget.MAIN_SCREEN -> {
                _mainScreenBackgroundProperties.value = currentLayout?.let {
                    LayoutBackgroundProperties(currentLayout.mainScreenLayout.backgroundId, currentLayout.mainScreenLayout.backgroundMode)
                }
            }
            LayoutTarget.SECONDARY_SCREEN -> {
                _secondaryScreenBackgroundProperties.value = currentLayout?.let {
                    LayoutBackgroundProperties(currentLayout.secondaryScreenLayout.backgroundId, currentLayout.secondaryScreenLayout.backgroundMode)
                }
            }
        }
    }

    fun currentLayoutHasName(): Boolean {
        return !_currentLayoutConfiguration.value?.name.isNullOrEmpty()
    }
}