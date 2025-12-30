package me.magnum.melonds.impl.layout

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import me.magnum.melonds.domain.model.Point
import me.magnum.melonds.domain.model.layout.LayoutConfiguration
import me.magnum.melonds.domain.model.layout.LayoutDisplayPair
import me.magnum.melonds.domain.model.layout.ScreenFold
import me.magnum.melonds.domain.model.layout.UILayout
import me.magnum.melonds.domain.model.layout.UILayoutVariant
import me.magnum.melonds.domain.model.ui.Orientation

class UILayoutProvider(private val defaultLayoutProvider: DefaultLayoutProvider) {

    private val currentUiSize = MutableStateFlow<Point?>(null)
    private val currentOrientation = MutableStateFlow<Orientation?>(null)
    private val currentFolds = MutableStateFlow<List<ScreenFold>?>(null)
    private val currentDisplays = MutableStateFlow<LayoutDisplayPair?>(null)

    private val currentLayoutVariant = combine(currentUiSize, currentOrientation, currentFolds, currentDisplays) { size, orientation, folds, displays ->
        if (size == null || orientation == null || folds == null || displays == null) {
            null
        } else {
            UILayoutVariant(size, orientation, folds, displays)
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

    fun updateDisplays(displays: LayoutDisplayPair) {
        currentDisplays.value = displays
    }

    fun setCurrentLayoutConfiguration(layoutConfiguration: LayoutConfiguration) {
        _currentLayoutConfiguration.value = layoutConfiguration
    }

    private fun getOptimalLayoutForVariant(layoutConfiguration: LayoutConfiguration, variant: UILayoutVariant): UILayout {
        val exactLayoutMatch = layoutConfiguration.layoutVariants[variant]
        if (exactLayoutMatch != null) {
            return populateLayoutIfRequired(exactLayoutMatch, variant)
        }

        val bestLayoutVariant = findSimilarLayoutVariant(layoutConfiguration, variant)
        if (bestLayoutVariant != null) {
            return populateLayoutIfRequired(bestLayoutVariant, variant)
        }

        return defaultLayoutProvider.buildDefaultLayout(variant)
    }

    /**
     * Populates the given [layout] with default components if they were not placed by the user.
     */
    private fun populateLayoutIfRequired(layout: UILayout, variant: UILayoutVariant): UILayout {
        val mainScreenRequiresDefaultLayout = layout.mainScreenLayout.components == null
        val secondaryScreenRequiresDefaultLayout = layout.secondaryScreenLayout.components == null && variant.displays.secondaryScreenDisplay != null
        val requiresDefaultLayout = mainScreenRequiresDefaultLayout || secondaryScreenRequiresDefaultLayout

        return if (requiresDefaultLayout) {
            val defaultLayout = defaultLayoutProvider.buildDefaultLayout(variant)
            val mainScreenLayout = if (mainScreenRequiresDefaultLayout) {
                layout.mainScreenLayout.copy(components = defaultLayout.mainScreenLayout.components)
            } else {
                layout.mainScreenLayout
            }
            val secondaryScreenLayout = if (secondaryScreenRequiresDefaultLayout) {
                layout.secondaryScreenLayout.copy(components = defaultLayout.secondaryScreenLayout.components)
            } else {
                layout.secondaryScreenLayout
            }
            return UILayout(mainScreenLayout, secondaryScreenLayout)
        } else {
            layout
        }
    }

    /**
     * Attempts to find a layout variant compatible with the given [variant]. If an exact match isn't available, this method falls back to any variant that shares the same orientation, ignoring size differences.
     */
    private fun findSimilarLayoutVariant(
        layoutConfiguration: LayoutConfiguration,
        variant: UILayoutVariant,
    ): UILayout? {
        if (variant.displays.secondaryScreenDisplay != null) {
            // The device has 2 displays. Try to find an equal layout with the screens swapped. Strip remaining variant information since it only refers to the main screen
            val swappedDisplayVariant = variant.copy(
                uiSize = Point(),
                orientation = Orientation.PORTRAIT,
                folds = emptyList(),
                displays = variant.displays.copy(
                    mainScreenDisplay = variant.displays.secondaryScreenDisplay,
                    secondaryScreenDisplay = variant.displays.mainScreenDisplay,
                ),
            )
            val swappedDisplaysLayoutConfigurationVariants = layoutConfiguration.layoutVariants.map {
                it.key.copy(uiSize = Point(), orientation = Orientation.PORTRAIT, folds = emptyList()) to it.value
            }.toMap()
            val swappedDisplayMatch = swappedDisplaysLayoutConfigurationVariants[swappedDisplayVariant]
            if (swappedDisplayMatch != null) {
                // Swap the layouts so that they match the original configuration
                return swappedDisplayMatch.copy(
                    mainScreenLayout = swappedDisplayMatch.secondaryScreenLayout,
                    secondaryScreenLayout = swappedDisplayMatch.mainScreenLayout,
                )
            }

            // Try to find a similar secondary display configuration (same display type and size). Strip identifying information to compare display specs
            val strippedDisplayInformation = variant.displays.copy(
                secondaryScreenDisplay = variant.displays.secondaryScreenDisplay.copy(id = -1),
            )
            val strippedLayoutConfigurationVariants = layoutConfiguration.layoutVariants.map {
                val strippedDisplays = it.key.displays.copy(
                    secondaryScreenDisplay = it.key.displays.secondaryScreenDisplay?.copy(id = -1),
                )
                it.key.copy(displays = strippedDisplays) to it.value
            }.toMap()

            val simplifiedVariant = variant.copy(displays = strippedDisplayInformation)
            val equivalentMatch = strippedLayoutConfigurationVariants[simplifiedVariant]
            if (equivalentMatch != null) {
                return equivalentMatch
            }

            // Find a layout without a secondary display but with a comparable main screen configuration. Reuse the main screen config, but adjust screen components to match
            // the default layout
            val noSecondaryDisplayVariant = variant.copy(displays = variant.displays.copy(secondaryScreenDisplay = null))
            val noSecondaryDisplayMatch = layoutConfiguration.layoutVariants[noSecondaryDisplayVariant]
            if (noSecondaryDisplayMatch != null) {
                // Copy screen layouts from the default layout
                val defaultLayout = defaultLayoutProvider.buildDefaultLayout(variant)
                val mainScreenLayoutComponents = noSecondaryDisplayMatch.mainScreenLayout.components.orEmpty().toMutableList().apply {
                    removeAll { it.isScreen() }
                    defaultLayout.mainScreenLayout.components?.filter { it.isScreen() }?.let {
                        addAll(0, it)
                    }
                }
                val mainScreenLayout = noSecondaryDisplayMatch.mainScreenLayout.copy(components = mainScreenLayoutComponents)
                return noSecondaryDisplayMatch.copy(
                    mainScreenLayout = mainScreenLayout,
                    secondaryScreenLayout = defaultLayout.secondaryScreenLayout,
                )
            }
        }

        val bestVariant = layoutConfiguration.layoutVariants.entries.firstOrNull {
            it.key.orientation == variant.orientation && it.key.uiSize == variant.uiSize
        }
        return bestVariant?.value
    }
}