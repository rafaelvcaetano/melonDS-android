package me.magnum.melonds.impl.layout

import me.magnum.melonds.domain.model.Rect
import me.magnum.melonds.domain.model.SCREEN_HEIGHT
import me.magnum.melonds.domain.model.SCREEN_WIDTH
import me.magnum.melonds.domain.model.consoleAspectRatio
import me.magnum.melonds.domain.model.layout.LayoutComponent
import me.magnum.melonds.domain.model.layout.LayoutDisplay
import me.magnum.melonds.domain.model.layout.PositionedLayoutComponent
import me.magnum.melonds.domain.model.layout.ScreenFold
import me.magnum.melonds.domain.model.layout.ScreenLayout
import me.magnum.melonds.domain.model.layout.UILayout
import me.magnum.melonds.domain.model.layout.UILayoutVariant
import me.magnum.melonds.domain.model.ui.Orientation
import me.magnum.melonds.impl.ScreenUnitsConverter
import kotlin.math.min
import kotlin.math.roundToInt

class DefaultLayoutProvider(private val screenUnitsConverter: ScreenUnitsConverter) {

    fun buildDefaultLayout(variant: UILayoutVariant): UILayout {
        val width = variant.uiSize.x
        val height = variant.uiSize.y
        val orientation = variant.orientation
        val folds = variant.folds
        val mainDisplay = variant.displays.mainScreenDisplay
        val secondaryDisplay = variant.displays.secondaryScreenDisplay

        val (mainScreenLayout, secondaryScreenLayout) = if (secondaryDisplay != null) {
            // Prioritise scenarios where a secondary display is present. In this scenario, one screen is shown in each display, and they should take the maximum available
            // space, independently of orientation or folds.

            // Check if it's a dual-screen device (both displays are built-in) or it's using an external display
            if (mainDisplay.type == LayoutDisplay.Type.BUILT_IN && secondaryDisplay.type == LayoutDisplay.Type.BUILT_IN) {
                // Assume that the default display is always the top one
                if (mainDisplay.isDefaultDisplay) {
                    val secondaryDisplayOrientation = if (secondaryDisplay.width > secondaryDisplay.height) Orientation.LANDSCAPE else Orientation.PORTRAIT
                    val secondaryLayout = when (secondaryDisplayOrientation) {
                        Orientation.PORTRAIT -> buildDefaultPortraitLayout(secondaryDisplay.width, secondaryDisplay.height, LayoutComponent.BOTTOM_SCREEN)
                        Orientation.LANDSCAPE -> buildDefaultLandscapeLayout(secondaryDisplay.width, secondaryDisplay.height, LayoutComponent.BOTTOM_SCREEN)
                    }
                    buildSingleScreenLayout(width, height, LayoutComponent.TOP_SCREEN) to secondaryLayout
                } else {
                    val mainLayout = when (orientation) {
                        Orientation.PORTRAIT -> buildDefaultPortraitLayout(width, height, LayoutComponent.BOTTOM_SCREEN)
                        Orientation.LANDSCAPE -> buildDefaultLandscapeLayout(width, height, LayoutComponent.BOTTOM_SCREEN)
                    }
                    mainLayout to buildSingleScreenLayout(secondaryDisplay.width, secondaryDisplay.height, LayoutComponent.TOP_SCREEN)
                }
            } else {
                // An external display is being used. Display the bottom screen on the main display, together with all soft input components
                val mainLayout = when (orientation) {
                    Orientation.PORTRAIT -> buildDefaultPortraitLayout(width, height, LayoutComponent.BOTTOM_SCREEN)
                    Orientation.LANDSCAPE -> buildDefaultLandscapeLayout(width, height, LayoutComponent.BOTTOM_SCREEN)
                }
                mainLayout to buildSingleScreenLayout(secondaryDisplay.width, secondaryDisplay.height, LayoutComponent.TOP_SCREEN)
            }
        } else {
            val mainScreenLayout = when (orientation) {
                Orientation.PORTRAIT -> {
                    if (folds.any { it.orientation == Orientation.LANDSCAPE }) {
                        // Flip-phone layout
                        buildDefaultFoldingPortraitLayout(width, height, folds)
                    } else {
                        // Simple portrait layout. Ignore vertical fold since there's no good way to support it
                        buildDefaultPortraitLayout(width, height)
                    }
                }
                Orientation.LANDSCAPE -> {
                    if (folds.any { it.orientation == Orientation.PORTRAIT }) {
                        // Book layout
                        buildDefaultFoldingLandscapeLayout(width, height, folds)
                    } else if (folds.any { it.orientation == Orientation.LANDSCAPE }) {
                        // Flip-phone layout
                        buildDefaultFoldingPortraitLayout(width, height, folds)
                    } else {
                        // No fold
                        buildDefaultLandscapeLayout(width, height)
                    }
                }
            }
            mainScreenLayout to ScreenLayout()
        }

        return UILayout(mainScreenLayout, secondaryScreenLayout)
    }

    private fun buildDefaultPortraitLayout(width: Int, height: Int, singleScreenComponent: LayoutComponent? = null): ScreenLayout {
        require(singleScreenComponent?.isScreen() != false) { "When specifying a single screen component, it must be a screen component" }

        val largeButtonsSize = screenUnitsConverter.dpToPixels(140f).toInt()
        val lrButtonsSize = screenUnitsConverter.dpToPixels(50f).toInt()
        val smallButtonsSize = screenUnitsConverter.dpToPixels(40f).toInt()
        val spacing4dp = screenUnitsConverter.dpToPixels(4f).toInt()

        var screenWidth = width
        var screenHeight = (width / consoleAspectRatio).toInt()

        val screenComponents = if (singleScreenComponent == null) {
            var screenMargin = 0
            if (screenHeight * 2 > height) {
                screenWidth = (height / 2 * consoleAspectRatio).toInt()
                screenHeight = height / 2
                screenMargin = (width - screenWidth) / 2
            }

            val topScreenView = Rect(screenMargin, 0, screenWidth, screenHeight)
            val bottomScreenView = Rect(screenMargin, screenHeight, screenWidth, screenHeight)

            arrayOf(
                PositionedLayoutComponent(topScreenView, LayoutComponent.TOP_SCREEN),
                PositionedLayoutComponent(bottomScreenView, LayoutComponent.BOTTOM_SCREEN),
            )
        } else {
            // Align screen to the top
            val screenArea = Rect(0, 0, screenWidth, screenHeight)
            arrayOf(PositionedLayoutComponent(screenArea, singleScreenComponent))
        }

        val dpadView = Rect(0, height - largeButtonsSize, largeButtonsSize, largeButtonsSize)
        val buttonsView = Rect(width - largeButtonsSize, height - largeButtonsSize, largeButtonsSize, largeButtonsSize)

        return ScreenLayout(
            listOf(
                *screenComponents,
                PositionedLayoutComponent(dpadView, LayoutComponent.DPAD),
                PositionedLayoutComponent(buttonsView, LayoutComponent.BUTTONS),
                PositionedLayoutComponent(Rect(0, screenHeight, lrButtonsSize, lrButtonsSize), LayoutComponent.BUTTON_L),
                PositionedLayoutComponent(Rect(width - lrButtonsSize, screenHeight, lrButtonsSize, lrButtonsSize), LayoutComponent.BUTTON_R),
                PositionedLayoutComponent(Rect(width / 2 - smallButtonsSize - spacing4dp / 2, height - smallButtonsSize, smallButtonsSize, smallButtonsSize), LayoutComponent.BUTTON_SELECT),
                PositionedLayoutComponent(Rect(width / 2 + spacing4dp / 2, height - smallButtonsSize, smallButtonsSize, smallButtonsSize), LayoutComponent.BUTTON_START),
                PositionedLayoutComponent(Rect(width / 2 - (smallButtonsSize * 2.0 + spacing4dp * 1.5).toInt(), screenHeight, smallButtonsSize, smallButtonsSize), LayoutComponent.BUTTON_HINGE),
                PositionedLayoutComponent(Rect(width / 2 - smallButtonsSize - (spacing4dp / 2.0).toInt(), screenHeight, smallButtonsSize, smallButtonsSize), LayoutComponent.BUTTON_TOGGLE_SOFT_INPUT),
                PositionedLayoutComponent(Rect(width / 2 + (spacing4dp / 2.0).toInt(), screenHeight, smallButtonsSize, smallButtonsSize), LayoutComponent.BUTTON_MICROPHONE_TOGGLE),
                PositionedLayoutComponent(Rect(width / 2 + smallButtonsSize + (spacing4dp * 1.5).toInt(), screenHeight, smallButtonsSize, smallButtonsSize), LayoutComponent.BUTTON_FAST_FORWARD_TOGGLE),
            )
        )
    }

    private fun buildDefaultLandscapeLayout(width: Int, height: Int, singleScreenComponent: LayoutComponent? = null): ScreenLayout {
        require(singleScreenComponent?.isScreen() != false) { "When specifying a single screen component, it must be a screen component" }

        val largeButtonsSize = screenUnitsConverter.dpToPixels(140f).toInt()
        val lrButtonsSize = screenUnitsConverter.dpToPixels(50f).toInt()
        val smallButtonsSize = screenUnitsConverter.dpToPixels(40f).toInt()
        val spacing4dp = screenUnitsConverter.dpToPixels(4f).toInt()

        val screenComponents = if (singleScreenComponent == null) {
            var topScreenWidth = (width * 0.66f).roundToInt()
            var topScreenHeight = (topScreenWidth / consoleAspectRatio).toInt()
            if (topScreenHeight > height) {
                topScreenWidth = (height * consoleAspectRatio).toInt()
                topScreenHeight = height
            }

            val topScreenView = Rect(0, 0, topScreenWidth, topScreenHeight)
            val bottomScreenWidth = width - topScreenWidth
            val bottomScreenHeight = (bottomScreenWidth / consoleAspectRatio).toInt()
            val bottomScreenView = Rect(topScreenWidth, 0, bottomScreenWidth, bottomScreenHeight)

            arrayOf(
                PositionedLayoutComponent(topScreenView, LayoutComponent.TOP_SCREEN),
                PositionedLayoutComponent(bottomScreenView, LayoutComponent.BOTTOM_SCREEN),
            )
        } else {
            val screenArea = centerScreenIn(width, height)
            arrayOf(PositionedLayoutComponent(screenArea, singleScreenComponent))
        }

        val dpadView = Rect(0, height - largeButtonsSize, largeButtonsSize, largeButtonsSize)
        val buttonsView = Rect(width - largeButtonsSize, height - largeButtonsSize, largeButtonsSize, largeButtonsSize)

        return ScreenLayout(
            listOf(
                *screenComponents,
                PositionedLayoutComponent(dpadView, LayoutComponent.DPAD),
                PositionedLayoutComponent(buttonsView, LayoutComponent.BUTTONS),
                PositionedLayoutComponent(Rect(0, 0, lrButtonsSize, lrButtonsSize), LayoutComponent.BUTTON_L),
                PositionedLayoutComponent(Rect(width - lrButtonsSize, 0, lrButtonsSize, lrButtonsSize), LayoutComponent.BUTTON_R),
                PositionedLayoutComponent(Rect((width - spacing4dp) / 2 - smallButtonsSize, height - smallButtonsSize, smallButtonsSize, smallButtonsSize), LayoutComponent.BUTTON_SELECT),
                PositionedLayoutComponent(Rect((width + spacing4dp) / 2, height - smallButtonsSize, smallButtonsSize, smallButtonsSize), LayoutComponent.BUTTON_START),
                PositionedLayoutComponent(Rect(width / 2 - (smallButtonsSize * 2.0 + spacing4dp * 1.5).toInt(), 0, smallButtonsSize, smallButtonsSize), LayoutComponent.BUTTON_HINGE),
                PositionedLayoutComponent(Rect(width / 2 - smallButtonsSize - (spacing4dp / 2.0).toInt(), 0, smallButtonsSize, smallButtonsSize), LayoutComponent.BUTTON_TOGGLE_SOFT_INPUT),
                PositionedLayoutComponent(Rect(width / 2 + (spacing4dp / 2.0).toInt(), 0, smallButtonsSize, smallButtonsSize), LayoutComponent.BUTTON_MICROPHONE_TOGGLE),
                PositionedLayoutComponent(Rect(width / 2 + smallButtonsSize + (spacing4dp * 1.5).toInt(), 0, smallButtonsSize, smallButtonsSize), LayoutComponent.BUTTON_FAST_FORWARD_TOGGLE),
            )
        )
    }

    /**
     * Creates a portrait layout that supports a horizontal fold. Screens are attached to either sides of the fold and as large as they can be to fit the screen.
     */
    private fun buildDefaultFoldingPortraitLayout(width: Int, height: Int, folds: List<ScreenFold>): ScreenLayout {
        // Only one fold is supported for now
        val mainFold = folds.first()

        val largeButtonsSize = screenUnitsConverter.dpToPixels(140f).toInt()
        val lrButtonsSize = screenUnitsConverter.dpToPixels(50f).toInt()
        val smallButtonsSize = screenUnitsConverter.dpToPixels(40f).toInt()
        val spacing4dp = screenUnitsConverter.dpToPixels(4f).toInt()

        var screenWidth = width
        var screenHeight = (width / consoleAspectRatio).toInt()
        val topHalfHeight = mainFold.foldBounds.y
        val bottomHalfHeight = height - mainFold.foldBounds.bottom
        var screenMargin = 0
        if (screenHeight > topHalfHeight || screenHeight > bottomHalfHeight) {
            val limitingHeight = min(topHalfHeight, bottomHalfHeight)
            screenWidth = (limitingHeight * consoleAspectRatio).toInt()
            screenHeight = limitingHeight
            screenMargin = (width - screenWidth) / 2
        }

        val topScreenView = Rect(screenMargin, mainFold.foldBounds.y - screenHeight, screenWidth, screenHeight)
        val bottomScreenView = Rect(screenMargin, mainFold.foldBounds.bottom, screenWidth, screenHeight)
        val dpadView = Rect(0, height - largeButtonsSize, largeButtonsSize, largeButtonsSize)
        val buttonsView = Rect(width - largeButtonsSize, height - largeButtonsSize, largeButtonsSize, largeButtonsSize)

        return ScreenLayout(
            listOf(
                PositionedLayoutComponent(topScreenView, LayoutComponent.TOP_SCREEN),
                PositionedLayoutComponent(bottomScreenView, LayoutComponent.BOTTOM_SCREEN),
                PositionedLayoutComponent(dpadView, LayoutComponent.DPAD),
                PositionedLayoutComponent(buttonsView, LayoutComponent.BUTTONS),
                PositionedLayoutComponent(Rect(0, mainFold.foldBounds.bottom, lrButtonsSize, lrButtonsSize), LayoutComponent.BUTTON_L),
                PositionedLayoutComponent(Rect(width - lrButtonsSize, mainFold.foldBounds.bottom, lrButtonsSize, lrButtonsSize), LayoutComponent.BUTTON_R),
                PositionedLayoutComponent(Rect(width / 2 - smallButtonsSize - spacing4dp / 2, height - smallButtonsSize, smallButtonsSize, smallButtonsSize), LayoutComponent.BUTTON_SELECT),
                PositionedLayoutComponent(Rect(width / 2 + spacing4dp / 2, height - smallButtonsSize, smallButtonsSize, smallButtonsSize), LayoutComponent.BUTTON_START),
                PositionedLayoutComponent(Rect(width / 2 - (smallButtonsSize * 2.0 + spacing4dp * 1.5).toInt(), mainFold.foldBounds.bottom, smallButtonsSize, smallButtonsSize), LayoutComponent.BUTTON_HINGE),
                PositionedLayoutComponent(Rect(width / 2 - smallButtonsSize - (spacing4dp / 2.0).toInt(), mainFold.foldBounds.bottom, smallButtonsSize, smallButtonsSize), LayoutComponent.BUTTON_TOGGLE_SOFT_INPUT),
                PositionedLayoutComponent(Rect(width / 2 + spacing4dp + (spacing4dp / 2.0).toInt(), mainFold.foldBounds.bottom, smallButtonsSize, smallButtonsSize), LayoutComponent.BUTTON_MICROPHONE_TOGGLE),
                PositionedLayoutComponent(Rect(width / 2 + smallButtonsSize + (spacing4dp * 1.5).toInt(), mainFold.foldBounds.bottom, smallButtonsSize, smallButtonsSize), LayoutComponent.BUTTON_FAST_FORWARD_TOGGLE),
            )
        )
    }

    /**
     * Creates a landscape layout that supports a vertical fold. Screens are attached to either sides of the fold and as large as they can be to fit the screen.
     */
    private fun buildDefaultFoldingLandscapeLayout(width: Int, height: Int, folds: List<ScreenFold>): ScreenLayout {
        // Only one fold is supported for now
        val mainFold = folds.first()

        val largeButtonsSize = screenUnitsConverter.dpToPixels(140f).toInt()
        val lrButtonsSize = screenUnitsConverter.dpToPixels(50f).toInt()
        val smallButtonsSize = screenUnitsConverter.dpToPixels(40f).toInt()
        val spacing8dp = screenUnitsConverter.dpToPixels(8f).toInt()

        // Position to the left of the fold, attached to the fold. As big as the screen allows
        var topScreenWidth = mainFold.foldBounds.x
        var topScreenHeight = (topScreenWidth / consoleAspectRatio).toInt()
        if (topScreenHeight > height) {
            topScreenHeight = height
            topScreenWidth = (height * consoleAspectRatio).toInt()
        }

        // Position to the right of the fold, attached to the fold. As big as the screen allows
        var bottomScreenWidth = width - mainFold.foldBounds.right
        var bottomScreenHeight = (bottomScreenWidth / consoleAspectRatio).toInt()
        if (bottomScreenHeight > height) {
            bottomScreenHeight = height
            bottomScreenWidth = (height * consoleAspectRatio).toInt()
        }

        // Check if the screens are small enough to be position under the L/R buttons
        val screenYPos = if (topScreenHeight < (height - lrButtonsSize - spacing8dp) && bottomScreenHeight < (height - lrButtonsSize - spacing8dp)) {
            lrButtonsSize + spacing8dp
        } else {
            0
        }

        val topScreenView = Rect(mainFold.foldBounds.x - topScreenWidth, screenYPos, topScreenWidth, topScreenHeight)
        val bottomScreenView = Rect(mainFold.foldBounds.right, screenYPos, bottomScreenWidth, bottomScreenHeight)
        val dpadView = Rect(0, height - largeButtonsSize, largeButtonsSize, largeButtonsSize)
        val buttonsView = Rect(width - largeButtonsSize, height - largeButtonsSize, largeButtonsSize, largeButtonsSize)

        return ScreenLayout(
            listOf(
                PositionedLayoutComponent(topScreenView, LayoutComponent.TOP_SCREEN),
                PositionedLayoutComponent(bottomScreenView, LayoutComponent.BOTTOM_SCREEN),
                PositionedLayoutComponent(dpadView, LayoutComponent.DPAD),
                PositionedLayoutComponent(buttonsView, LayoutComponent.BUTTONS),
                PositionedLayoutComponent(Rect(0, 0, lrButtonsSize, lrButtonsSize), LayoutComponent.BUTTON_L),
                PositionedLayoutComponent(Rect(width - lrButtonsSize, 0, lrButtonsSize, lrButtonsSize), LayoutComponent.BUTTON_R),
                PositionedLayoutComponent(Rect(mainFold.foldBounds.x - smallButtonsSize - spacing8dp, height - smallButtonsSize, smallButtonsSize, smallButtonsSize), LayoutComponent.BUTTON_SELECT),
                PositionedLayoutComponent(Rect(mainFold.foldBounds.right + spacing8dp, height - smallButtonsSize, smallButtonsSize, smallButtonsSize), LayoutComponent.BUTTON_START),
                PositionedLayoutComponent(Rect(mainFold.foldBounds.x - smallButtonsSize * 2 - spacing8dp * 2, 0, smallButtonsSize, smallButtonsSize), LayoutComponent.BUTTON_HINGE),
                PositionedLayoutComponent(Rect(mainFold.foldBounds.x - smallButtonsSize - spacing8dp, 0, smallButtonsSize, smallButtonsSize), LayoutComponent.BUTTON_TOGGLE_SOFT_INPUT),
                PositionedLayoutComponent(Rect(mainFold.foldBounds.right + smallButtonsSize + spacing8dp, 0, smallButtonsSize, smallButtonsSize), LayoutComponent.BUTTON_MICROPHONE_TOGGLE),
                PositionedLayoutComponent(Rect(mainFold.foldBounds.right + spacing8dp * 2, 0, smallButtonsSize, smallButtonsSize), LayoutComponent.BUTTON_FAST_FORWARD_TOGGLE),
            )
        )
    }

    private fun buildSingleScreenLayout(width: Int, height: Int, screenComponent: LayoutComponent): ScreenLayout {
        val screenView = centerScreenIn(width, height)
        val positionedScreenComponent = PositionedLayoutComponent(screenView, screenComponent)

        return ScreenLayout(listOf(positionedScreenComponent))
    }

    private fun centerScreenIn(width: Int, height: Int): Rect {
        val areaAspectRatio = width.toFloat() / height
        return if (areaAspectRatio > consoleAspectRatio) {
            // Center horizontally
            val scale = height.toFloat() / SCREEN_HEIGHT
            val scaledWidth = (SCREEN_WIDTH * scale).toInt()
            Rect((width - scaledWidth) / 2, 0, scaledWidth, height)
        } else {
            // Center vertically
            val scale = width.toFloat() / SCREEN_WIDTH
            val scaledHeight = (SCREEN_HEIGHT * scale).toInt()
            Rect(0, (height - scaledHeight) / 2, width, scaledHeight)
        }
    }
}