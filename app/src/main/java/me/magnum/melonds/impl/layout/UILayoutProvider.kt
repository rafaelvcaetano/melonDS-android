package me.magnum.melonds.impl.layout

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.onSubscription
import me.magnum.melonds.domain.model.layout.LayoutConfiguration
import me.magnum.melonds.domain.model.Point
import me.magnum.melonds.domain.model.layout.UILayout
import me.magnum.melonds.domain.model.ui.Orientation
import me.magnum.melonds.domain.model.layout.ScreenFold
import me.magnum.melonds.domain.model.layout.UILayoutVariant
import me.magnum.melonds.impl.DefaultLayoutProvider

class UILayoutProvider(private val defaultLayoutProvider: DefaultLayoutProvider) {

    private val currentUiSize = MutableStateFlow<Point?>(null)
    private val currentOrientation = MutableStateFlow<Orientation?>(null)
    private val currentFolds = MutableStateFlow<List<ScreenFold>?>(null)

    private val currentLayoutVariant = combine(currentUiSize, currentOrientation, currentFolds) { size, orientation, folds ->
        if (size == null || orientation == null || folds == null) {
            null
        } else {
            UILayoutVariant(size, orientation, folds)
        }
    }.distinctUntilChanged()

    private val _currentLayoutConfiguration = MutableStateFlow<LayoutConfiguration?>(null)

    val currentLayout = combine(_currentLayoutConfiguration, currentLayoutVariant) { layoutConfiguration, variant ->
        if (layoutConfiguration == null || variant == null) {
            null
        } else {
            variant to getOptimalLayoutForVariant(layoutConfiguration, variant)
        }
    }

    fun updateCurrentOrientation(orientation: Orientation) {
        currentOrientation.value = orientation
    }

    fun updateUiSize(width: Int, height: Int) {
        currentUiSize.value = Point(width, height)
    }

    fun updateFolds(folds: List<ScreenFold>) {
        currentFolds.value = folds
    }

    fun setCurrentLayoutConfiguration(layoutConfiguration: LayoutConfiguration) {
        _currentLayoutConfiguration.value = layoutConfiguration
    }

    private fun getOptimalLayoutForVariant(layoutConfiguration: LayoutConfiguration, variant: UILayoutVariant): UILayout {
        val exactLayoutMatch = layoutConfiguration.layoutVariants[variant]
        if (exactLayoutMatch != null) {
            return if (exactLayoutMatch.components == null) {
                val defaultLayout = defaultLayoutProvider.buildDefaultLayout(variant.uiSize.x, variant.uiSize.y, variant.orientation, variant.folds)
                return exactLayoutMatch.copy(components = defaultLayout.components)
            } else {
                exactLayoutMatch
            }
        }

        val bestLayoutVariant = findSimilarLayoutVariant(layoutConfiguration, variant)
        if (bestLayoutVariant != null) {
            return if (bestLayoutVariant.components == null) {
                val defaultLayout = defaultLayoutProvider.buildDefaultLayout(variant.uiSize.x, variant.uiSize.y, variant.orientation, variant.folds)
                return bestLayoutVariant.copy(components = defaultLayout.components)
            } else {
                bestLayoutVariant
            }
        }

        return defaultLayoutProvider.buildDefaultLayout(variant.uiSize.x, variant.uiSize.y, variant.orientation, variant.folds)
    }

    private fun findSimilarLayoutVariant(layoutConfiguration: LayoutConfiguration, variant: UILayoutVariant): UILayout? {
        val bestVariantInSameOrientation = layoutConfiguration.layoutVariants.keys.firstOrNull {
            it.orientation == variant.orientation && it.uiSize == variant.uiSize
        }
        return bestVariantInSameOrientation?.let { layoutConfiguration.layoutVariants[it] }
    }
}