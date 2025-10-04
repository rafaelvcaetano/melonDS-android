package me.magnum.melonds.impl.layout

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import me.magnum.melonds.domain.model.Point
import me.magnum.melonds.domain.model.Rect
import me.magnum.melonds.domain.model.layout.LayoutConfiguration
import me.magnum.melonds.domain.model.layout.ScreenFold
import me.magnum.melonds.domain.model.layout.UILayout
import me.magnum.melonds.domain.model.layout.UILayoutVariant
import me.magnum.melonds.domain.model.ui.Orientation
import me.magnum.melonds.impl.DefaultLayoutProvider
import kotlin.math.roundToInt

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
            val (matchedVariant, layout) = bestLayoutVariant

            val filledLayout = if (layout.components == null) {
                val defaultLayout = defaultLayoutProvider.buildDefaultLayout(
                    variant.uiSize.x,
                    variant.uiSize.y,
                    variant.orientation,
                    variant.folds,
                )
                layout.copy(components = defaultLayout.components)
            } else {
                layout
            }

            return if (matchedVariant.uiSize != variant.uiSize) {
                scaleLayout(filledLayout, matchedVariant.uiSize, variant.uiSize)
            } else {
                filledLayout
            }
        }

        return defaultLayoutProvider.buildDefaultLayout(variant.uiSize.x, variant.uiSize.y, variant.orientation, variant.folds)
    }

    /**
     * Attempts to find a layout variant compatible with the given [variant]. If an exact match isn't available, this method falls back to any variant that shares the same orientation, ignoring size differences.
     */
    private fun findSimilarLayoutVariant(
        layoutConfiguration: LayoutConfiguration,
        variant: UILayoutVariant,
    ): Map.Entry<UILayoutVariant, UILayout>? {
        val bestVariant = layoutConfiguration.layoutVariants.entries.firstOrNull {
            it.key.orientation == variant.orientation && it.key.uiSize == variant.uiSize
        }
        return if (bestVariant != null) {
            bestVariant
        } else {
            // Find a variant in the same orientation
            layoutConfiguration.layoutVariants.entries.firstOrNull {
                it.key.orientation == variant.orientation
            }
        }
    }

    private fun scaleLayout(layout: UILayout, fromSize: Point, toSize: Point): UILayout {
        val components = layout.components ?: return layout

        val scaleX = toSize.x.toFloat() / fromSize.x
        val scaleY = toSize.y.toFloat() / fromSize.y

        val scaledComponents = components.map {
            val rect = it.rect
            val scaledRect = Rect(
                (rect.x * scaleX).roundToInt(),
                (rect.y * scaleY).roundToInt(),
                (rect.width * scaleX).roundToInt(),
                (rect.height * scaleY).roundToInt(),
            )
            it.copy(rect = scaledRect)
        }

        return layout.copy(components = scaledComponents)
    }
}