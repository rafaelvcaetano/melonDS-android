package me.magnum.melonds.impl

import me.magnum.melonds.domain.model.layout.LayoutComponent
import me.magnum.melonds.domain.model.layout.PositionedLayoutComponent
import me.magnum.melonds.domain.model.Rect
import me.magnum.melonds.domain.model.layout.UILayout
import me.magnum.melonds.domain.model.ui.Orientation
import me.magnum.melonds.domain.model.layout.ScreenFold
import kotlin.math.min
import kotlin.math.roundToInt

class DefaultLayoutProvider(private val screenUnitsConverter: ScreenUnitsConverter) {
    companion object {
        private const val DS_ASPECT_RATIO = 256f / 192f
    }

    fun buildDefaultLayout(width: Int, height: Int, orientation: Orientation, folds: List<ScreenFold>): UILayout {
        return when(orientation) {
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
    }

    private fun buildDefaultPortraitLayout(width: Int, height: Int): UILayout {
        val largeButtonsSize = screenUnitsConverter.dpToPixels(140f).toInt()
        val lrButtonsSize = screenUnitsConverter.dpToPixels(50f).toInt()
        val smallButtonsSize = screenUnitsConverter.dpToPixels(40f).toInt()
        val spacing4dp = screenUnitsConverter.dpToPixels(4f).toInt()

        var screenWidth = width
        var screenHeight = (width / DS_ASPECT_RATIO).toInt()
        var screenMargin = 0
        if (screenHeight * 2 > height) {
            screenWidth = (height / 2 * DS_ASPECT_RATIO).toInt()
            screenHeight = height / 2
            screenMargin = (width - screenWidth) / 2
        }

        val topScreenView = Rect(screenMargin, 0, screenWidth, screenHeight)
        val bottomScreenView = Rect(screenMargin, screenHeight, screenWidth, screenHeight)
        val dpadView = Rect(0, height - largeButtonsSize, largeButtonsSize, largeButtonsSize)
        val buttonsView = Rect(width - largeButtonsSize, height - largeButtonsSize, largeButtonsSize, largeButtonsSize)

        return UILayout(
            listOf(
                PositionedLayoutComponent(topScreenView, LayoutComponent.TOP_SCREEN),
                PositionedLayoutComponent(bottomScreenView, LayoutComponent.BOTTOM_SCREEN),
                PositionedLayoutComponent(dpadView, LayoutComponent.DPAD),
                PositionedLayoutComponent(buttonsView, LayoutComponent.BUTTONS),
                PositionedLayoutComponent(Rect(0, screenHeight, lrButtonsSize, lrButtonsSize), LayoutComponent.BUTTON_L),
                PositionedLayoutComponent(Rect(width - lrButtonsSize, screenHeight, lrButtonsSize, lrButtonsSize), LayoutComponent.BUTTON_R),
                PositionedLayoutComponent(Rect(width / 2 - smallButtonsSize - spacing4dp / 2, height - smallButtonsSize, smallButtonsSize, smallButtonsSize), LayoutComponent.BUTTON_SELECT),
                PositionedLayoutComponent(Rect(width / 2 + spacing4dp / 2, height - smallButtonsSize, smallButtonsSize, smallButtonsSize), LayoutComponent.BUTTON_START),
                PositionedLayoutComponent(Rect(width / 2 - (smallButtonsSize * 2.0).toInt() - spacing4dp * 2, screenHeight, smallButtonsSize, smallButtonsSize), LayoutComponent.BUTTON_HINGE),
                PositionedLayoutComponent(Rect(width / 2 - smallButtonsSize, screenHeight, smallButtonsSize, smallButtonsSize), LayoutComponent.BUTTON_TOGGLE_SOFT_INPUT),
                PositionedLayoutComponent(Rect(width / 2 + spacing4dp * 2, screenHeight, smallButtonsSize, smallButtonsSize), LayoutComponent.BUTTON_FAST_FORWARD_TOGGLE),
                PositionedLayoutComponent(Rect(width / 2 + smallButtonsSize + spacing4dp * 4, screenHeight, smallButtonsSize, smallButtonsSize), LayoutComponent.BUTTON_MICROPHONE_TOGGLE),
                )
        )
    }

    private fun buildDefaultLandscapeLayout(width: Int, height: Int): UILayout {
        val largeButtonsSize = screenUnitsConverter.dpToPixels(140f).toInt()
        val lrButtonsSize = screenUnitsConverter.dpToPixels(50f).toInt()
        val smallButtonsSize = screenUnitsConverter.dpToPixels(40f).toInt()
        val spacing4dp = screenUnitsConverter.dpToPixels(4f).toInt()

        var topScreenWidth = (width * 0.66f).roundToInt()
        var topScreenHeight = (topScreenWidth / DS_ASPECT_RATIO).toInt()
        if (topScreenHeight > height) {
            topScreenWidth = (height * DS_ASPECT_RATIO).toInt()
            topScreenHeight = height
        }

        val topScreenView = Rect(0, 0, topScreenWidth, topScreenHeight)
        val bottomScreenWidth = width - topScreenWidth
        val bottomScreenHeight = (bottomScreenWidth / DS_ASPECT_RATIO).toInt()
        val bottomScreenView = Rect(topScreenWidth, 0, bottomScreenWidth, bottomScreenHeight)
        val dpadView = Rect(0, height - largeButtonsSize, largeButtonsSize, largeButtonsSize)
        val buttonsView = Rect(width - largeButtonsSize, height - largeButtonsSize, largeButtonsSize, largeButtonsSize)

        return UILayout(
            listOf(
                PositionedLayoutComponent(topScreenView, LayoutComponent.TOP_SCREEN),
                PositionedLayoutComponent(bottomScreenView, LayoutComponent.BOTTOM_SCREEN),
                PositionedLayoutComponent(dpadView, LayoutComponent.DPAD),
                PositionedLayoutComponent(buttonsView, LayoutComponent.BUTTONS),
                PositionedLayoutComponent(Rect(0, 0, lrButtonsSize, lrButtonsSize), LayoutComponent.BUTTON_L),
                PositionedLayoutComponent(Rect(width - lrButtonsSize, 0, lrButtonsSize, lrButtonsSize), LayoutComponent.BUTTON_R),
                PositionedLayoutComponent(Rect((width - spacing4dp) / 2 - smallButtonsSize, height - smallButtonsSize, smallButtonsSize, smallButtonsSize), LayoutComponent.BUTTON_SELECT),
                PositionedLayoutComponent(Rect((width + spacing4dp) / 2, height - smallButtonsSize, smallButtonsSize, smallButtonsSize), LayoutComponent.BUTTON_START),
                PositionedLayoutComponent(Rect(width / 2 - (smallButtonsSize * 2.0).toInt() - spacing4dp * 2, 0, smallButtonsSize, smallButtonsSize), LayoutComponent.BUTTON_HINGE),
                PositionedLayoutComponent(Rect(width / 2 - smallButtonsSize, 0, smallButtonsSize, smallButtonsSize), LayoutComponent.BUTTON_TOGGLE_SOFT_INPUT),
                PositionedLayoutComponent(Rect(width / 2 + spacing4dp * 2, 0, smallButtonsSize, smallButtonsSize), LayoutComponent.BUTTON_FAST_FORWARD_TOGGLE),
                PositionedLayoutComponent(Rect(width / 2 + smallButtonsSize + spacing4dp * 4, 0, smallButtonsSize, smallButtonsSize), LayoutComponent.BUTTON_MICROPHONE_TOGGLE),
                )
        )
    }

    /**
     * Creates a portrait layout that supports a horizontal fold. Screens are attached to either sides of the fold and as large as they can be to fit the screen.
     */
    private fun buildDefaultFoldingPortraitLayout(width: Int, height: Int, folds: List<ScreenFold>): UILayout {
        // Only one fold is supported for now
        val mainFold = folds.first()

        val largeButtonsSize = screenUnitsConverter.dpToPixels(140f).toInt()
        val lrButtonsSize = screenUnitsConverter.dpToPixels(50f).toInt()
        val smallButtonsSize = screenUnitsConverter.dpToPixels(40f).toInt()
        val spacing4dp = screenUnitsConverter.dpToPixels(4f).toInt()

        var screenWidth = width
        var screenHeight = (width / DS_ASPECT_RATIO).toInt()
        val topHalfHeight = mainFold.foldBounds.y
        val bottomHalfHeight = height - mainFold.foldBounds.bottom
        var screenMargin = 0
        if (screenHeight > topHalfHeight || screenHeight > bottomHalfHeight) {
            val limitingHeight = min(topHalfHeight, bottomHalfHeight)
            screenWidth = (limitingHeight * DS_ASPECT_RATIO).toInt()
            screenHeight = limitingHeight
            screenMargin = (width - screenWidth) / 2
        }

        val topScreenView = Rect(screenMargin, mainFold.foldBounds.y - screenHeight, screenWidth, screenHeight)
        val bottomScreenView = Rect(screenMargin, mainFold.foldBounds.bottom, screenWidth, screenHeight)
        val dpadView = Rect(0, height - largeButtonsSize, largeButtonsSize, largeButtonsSize)
        val buttonsView = Rect(width - largeButtonsSize, height - largeButtonsSize, largeButtonsSize, largeButtonsSize)

        return UILayout(
            listOf(
                PositionedLayoutComponent(topScreenView, LayoutComponent.TOP_SCREEN),
                PositionedLayoutComponent(bottomScreenView, LayoutComponent.BOTTOM_SCREEN),
                PositionedLayoutComponent(dpadView, LayoutComponent.DPAD),
                PositionedLayoutComponent(buttonsView, LayoutComponent.BUTTONS),
                PositionedLayoutComponent(Rect(0, mainFold.foldBounds.bottom, lrButtonsSize, lrButtonsSize), LayoutComponent.BUTTON_L),
                PositionedLayoutComponent(Rect(width - lrButtonsSize, mainFold.foldBounds.bottom, lrButtonsSize, lrButtonsSize), LayoutComponent.BUTTON_R),
                PositionedLayoutComponent(Rect(width / 2 - smallButtonsSize - spacing4dp / 2, height - smallButtonsSize, smallButtonsSize, smallButtonsSize), LayoutComponent.BUTTON_SELECT),
                PositionedLayoutComponent(Rect(width / 2 + spacing4dp / 2, height - smallButtonsSize, smallButtonsSize, smallButtonsSize), LayoutComponent.BUTTON_START),
                PositionedLayoutComponent(Rect(width / 2 - (smallButtonsSize * 1.5).toInt() - spacing4dp * 2, mainFold.foldBounds.bottom, smallButtonsSize, smallButtonsSize), LayoutComponent.BUTTON_HINGE),
                PositionedLayoutComponent(Rect(width / 2 - (smallButtonsSize * 0.5).toInt(), mainFold.foldBounds.bottom, smallButtonsSize, smallButtonsSize), LayoutComponent.BUTTON_TOGGLE_SOFT_INPUT),
                PositionedLayoutComponent(Rect(width / 2 + (smallButtonsSize * 0.5).toInt() + spacing4dp * 2, mainFold.foldBounds.bottom, smallButtonsSize, smallButtonsSize), LayoutComponent.BUTTON_FAST_FORWARD_TOGGLE),
            )
        )
    }

    /**
     * Creates a landscape layout that supports a vertical fold. Screens are attached to either sides of the fold and as large as they can be to fit the screen.
     */
    private fun buildDefaultFoldingLandscapeLayout(width: Int, height: Int, folds: List<ScreenFold>): UILayout {
        // Only one fold is supported for now
        val mainFold = folds.first()

        val largeButtonsSize = screenUnitsConverter.dpToPixels(140f).toInt()
        val lrButtonsSize = screenUnitsConverter.dpToPixels(50f).toInt()
        val smallButtonsSize = screenUnitsConverter.dpToPixels(40f).toInt()
        val spacing8dp = screenUnitsConverter.dpToPixels(8f).toInt()

        // Position to the left of the fold, attached to the fold. As big as the screen allows
        var topScreenWidth = mainFold.foldBounds.x
        var topScreenHeight = (topScreenWidth / DS_ASPECT_RATIO).toInt()
        if (topScreenHeight > height) {
            topScreenHeight = height
            topScreenWidth = (height * DS_ASPECT_RATIO).toInt()
        }

        // Position to the right of the fold, attached to the fold. As big as the screen allows
        var bottomScreenWidth = width - mainFold.foldBounds.right
        var bottomScreenHeight = (bottomScreenWidth / DS_ASPECT_RATIO).toInt()
        if (bottomScreenHeight > height) {
            bottomScreenHeight = height
            bottomScreenWidth = (height * DS_ASPECT_RATIO).toInt()
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

        return UILayout(
            listOf(
                PositionedLayoutComponent(topScreenView, LayoutComponent.TOP_SCREEN),
                PositionedLayoutComponent(bottomScreenView, LayoutComponent.BOTTOM_SCREEN),
                PositionedLayoutComponent(dpadView, LayoutComponent.DPAD),
                PositionedLayoutComponent(buttonsView, LayoutComponent.BUTTONS),
                PositionedLayoutComponent(Rect(0, 0, lrButtonsSize, lrButtonsSize), LayoutComponent.BUTTON_L),
                PositionedLayoutComponent(Rect(width - lrButtonsSize, 0, lrButtonsSize, lrButtonsSize), LayoutComponent.BUTTON_R),
                PositionedLayoutComponent(Rect(mainFold.foldBounds.x - smallButtonsSize - spacing8dp, height - smallButtonsSize, smallButtonsSize, smallButtonsSize), LayoutComponent.BUTTON_SELECT),
                PositionedLayoutComponent(Rect(mainFold.foldBounds.right + spacing8dp, height - smallButtonsSize, smallButtonsSize, smallButtonsSize), LayoutComponent.BUTTON_START),
                PositionedLayoutComponent(Rect(mainFold.foldBounds.x - smallButtonsSize - spacing8dp, 0, smallButtonsSize, smallButtonsSize), LayoutComponent.BUTTON_HINGE),
                PositionedLayoutComponent(Rect(mainFold.foldBounds.right + spacing8dp, 0, smallButtonsSize, smallButtonsSize), LayoutComponent.BUTTON_TOGGLE_SOFT_INPUT),
                PositionedLayoutComponent(Rect(mainFold.foldBounds.right + smallButtonsSize + spacing8dp * 2, 0, smallButtonsSize, smallButtonsSize), LayoutComponent.BUTTON_FAST_FORWARD_TOGGLE),
            )
        )
    }
}