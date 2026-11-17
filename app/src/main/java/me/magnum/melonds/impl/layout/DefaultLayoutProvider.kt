package me.magnum.melonds.impl.layout

import android.content.Context
import android.os.Build
import android.view.WindowInsets
import android.view.WindowManager
import androidx.core.content.getSystemService
import me.magnum.melonds.domain.model.Rect
import me.magnum.melonds.domain.model.SCREEN_HEIGHT
import me.magnum.melonds.domain.model.SCREEN_WIDTH
import me.magnum.melonds.domain.model.consoleAspectRatio
import me.magnum.melonds.domain.model.layout.Insets
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

class DefaultLayoutProvider(
    private val context: Context,
    private val screenUnitsConverter: ScreenUnitsConverter,
) {

    fun buildDefaultLayout(variant: UILayoutVariant): UILayout {
        val width = variant.uiSize.x
        val height = variant.uiSize.y
        val orientation = variant.orientation
        val folds = variant.folds
        val mainDisplay = variant.displays.mainScreenDisplay
        val secondaryDisplay = variant.displays.secondaryScreenDisplay
        val mainDisplayInsets = variant.uiInsets

        val (mainScreenLayout, secondaryScreenLayout) = if (secondaryDisplay != null) {
            val secondaryDisplayInsets = getDisplaySafeInsets(secondaryDisplay)

            // Prioritise scenarios where a secondary display is present. In this scenario, one screen is shown in each display, and they should take the maximum available
            // space, independently of orientation or folds.

            // Check if it's a dual-screen device (both displays are built-in) or it's using an external display
            if (mainDisplay.type == LayoutDisplay.Type.BUILT_IN && secondaryDisplay.type == LayoutDisplay.Type.BUILT_IN) {
                // Assume that the default display is always the top one
                if (mainDisplay.isDefaultDisplay) {
                    val secondaryDisplayOrientation = if (secondaryDisplay.width > secondaryDisplay.height) Orientation.LANDSCAPE else Orientation.PORTRAIT
                    val secondaryLayout = when (secondaryDisplayOrientation) {
                        Orientation.PORTRAIT -> buildDefaultPortraitLayout(secondaryDisplay.width, secondaryDisplay.height, secondaryDisplayInsets, LayoutComponent.BOTTOM_SCREEN)
                        Orientation.LANDSCAPE -> buildDefaultLandscapeLayout(secondaryDisplay.width, secondaryDisplay.height, secondaryDisplayInsets, LayoutComponent.BOTTOM_SCREEN)
                    }
                    buildSingleScreenLayout(width, height, LayoutComponent.TOP_SCREEN) to secondaryLayout
                } else {
                    val mainLayout = when (orientation) {
                        Orientation.PORTRAIT -> buildDefaultPortraitLayout(width, height, mainDisplayInsets, LayoutComponent.BOTTOM_SCREEN)
                        Orientation.LANDSCAPE -> buildDefaultLandscapeLayout(width, height, mainDisplayInsets, LayoutComponent.BOTTOM_SCREEN)
                    }
                    mainLayout to buildSingleScreenLayout(secondaryDisplay.width, secondaryDisplay.height, LayoutComponent.TOP_SCREEN)
                }
            } else {
                // An external display is being used. Display the bottom screen on the main display, together with all soft input components
                val mainLayout = when (orientation) {
                    Orientation.PORTRAIT -> buildDefaultPortraitLayout(width, height, mainDisplayInsets, LayoutComponent.BOTTOM_SCREEN)
                    Orientation.LANDSCAPE -> buildDefaultLandscapeLayout(width, height, mainDisplayInsets, LayoutComponent.BOTTOM_SCREEN)
                }
                mainLayout to buildSingleScreenLayout(secondaryDisplay.width, secondaryDisplay.height, LayoutComponent.TOP_SCREEN)
            }
        } else {
            val mainScreenLayout = when (orientation) {
                Orientation.PORTRAIT -> {
                    if (folds.any { it.orientation == Orientation.LANDSCAPE }) {
                        // Flip-phone layout
                        buildDefaultFoldingPortraitLayout(width, height, folds, mainDisplayInsets)
                    } else {
                        // Simple portrait layout. Ignore vertical fold since there's no good way to support it
                        buildDefaultPortraitLayout(width, height, mainDisplayInsets)
                    }
                }
                Orientation.LANDSCAPE -> {
                    if (folds.any { it.orientation == Orientation.PORTRAIT }) {
                        // Book layout
                        buildDefaultFoldingLandscapeLayout(width, height, folds, mainDisplayInsets)
                    } else if (folds.any { it.orientation == Orientation.LANDSCAPE }) {
                        // Flip-phone layout
                        buildDefaultFoldingPortraitLayout(width, height, folds, mainDisplayInsets)
                    } else {
                        // No fold
                        buildDefaultLandscapeLayout(width, height, mainDisplayInsets)
                    }
                }
            }
            mainScreenLayout to ScreenLayout()
        }

        return UILayout(mainScreenLayout, secondaryScreenLayout)
    }

    private fun buildDefaultPortraitLayout(width: Int, height: Int, insets: Insets, singleScreenComponent: LayoutComponent? = null): ScreenLayout {
        require(singleScreenComponent?.isScreen() != false) { "When specifying a single screen component, it must be a screen component" }

        val safeLeft = insets.left
        val safeTop = insets.top
        val safeRight = insets.right
        val safeBottom = insets.bottom
        val safeWidth = width - safeLeft - safeRight
        val safeHeight = height - safeTop - safeBottom

        val largeButtonsSize = screenUnitsConverter.dpToPixels(140f).toInt()
        val lrButtonsSize = screenUnitsConverter.dpToPixels(50f).toInt()
        val smallButtonsSize = screenUnitsConverter.dpToPixels(40f).toInt()
        val spacing4dp = screenUnitsConverter.dpToPixels(4f).toInt()

        var screenWidth = safeWidth
        var screenHeight = (safeWidth / consoleAspectRatio).toInt()

        val screenComponents = if (singleScreenComponent == null) {
            var screenMargin = 0
            if (screenHeight * 2 > safeHeight) {
                screenWidth = (safeHeight / 2 * consoleAspectRatio).toInt()
                screenHeight = safeHeight / 2
                screenMargin = (safeWidth - screenWidth) / 2
            }

            val topScreenView = Rect(safeLeft + screenMargin, safeTop, screenWidth, screenHeight)
            val bottomScreenView = Rect(safeLeft + screenMargin, safeTop + screenHeight, screenWidth, screenHeight)

            arrayOf(
                PositionedLayoutComponent(topScreenView, LayoutComponent.TOP_SCREEN),
                PositionedLayoutComponent(bottomScreenView, LayoutComponent.BOTTOM_SCREEN),
            )
        } else {
            // Align screen to the top of the safe area
            val screenArea = Rect(safeLeft, safeTop, screenWidth, screenHeight)
            arrayOf(PositionedLayoutComponent(screenArea, singleScreenComponent))
        }

        val screensBottom = if (singleScreenComponent == null) safeTop + screenHeight * 2 else safeTop + screenHeight
        // Check if there's space to put all the buttons below the screens. If not, place L, R, and utility buttons aligned with the top of the bottom screen
        val utilityButtonsTop = if (screensBottom + lrButtonsSize + largeButtonsSize > height) {
            safeTop + screenHeight
        } else {
            screensBottom
        }

        val dpadView = Rect(safeLeft, height - safeBottom - largeButtonsSize, largeButtonsSize, largeButtonsSize)
        val buttonsView = Rect(width - safeRight - largeButtonsSize, height - safeBottom - largeButtonsSize, largeButtonsSize, largeButtonsSize)

        return ScreenLayout(
            listOf(
                *screenComponents,
                PositionedLayoutComponent(dpadView, LayoutComponent.DPAD),
                PositionedLayoutComponent(buttonsView, LayoutComponent.BUTTONS),
                PositionedLayoutComponent(Rect(safeLeft, utilityButtonsTop, lrButtonsSize, lrButtonsSize), LayoutComponent.BUTTON_L),
                PositionedLayoutComponent(Rect(width - safeRight - lrButtonsSize, utilityButtonsTop, lrButtonsSize, lrButtonsSize), LayoutComponent.BUTTON_R),
                PositionedLayoutComponent(Rect(width / 2 - smallButtonsSize - spacing4dp / 2, height - safeBottom - smallButtonsSize, smallButtonsSize, smallButtonsSize), LayoutComponent.BUTTON_SELECT),
                PositionedLayoutComponent(Rect(width / 2 + spacing4dp / 2, height - safeBottom - smallButtonsSize, smallButtonsSize, smallButtonsSize), LayoutComponent.BUTTON_START),
                PositionedLayoutComponent(Rect(width / 2 - (smallButtonsSize * 2.0 + spacing4dp * 1.5).toInt(), utilityButtonsTop, smallButtonsSize, smallButtonsSize), LayoutComponent.BUTTON_HINGE),
                PositionedLayoutComponent(Rect(width / 2 - smallButtonsSize - (spacing4dp / 2.0).toInt(), utilityButtonsTop, smallButtonsSize, smallButtonsSize), LayoutComponent.BUTTON_TOGGLE_SOFT_INPUT),
                PositionedLayoutComponent(Rect(width / 2 + (spacing4dp / 2.0).toInt(), utilityButtonsTop, smallButtonsSize, smallButtonsSize), LayoutComponent.BUTTON_MICROPHONE_TOGGLE),
                PositionedLayoutComponent(Rect(width / 2 + smallButtonsSize + (spacing4dp * 1.5).toInt(), utilityButtonsTop, smallButtonsSize, smallButtonsSize), LayoutComponent.BUTTON_FAST_FORWARD_TOGGLE),
            )
        )
    }

    private fun buildDefaultLandscapeLayout(width: Int, height: Int, insets: Insets, singleScreenComponent: LayoutComponent? = null): ScreenLayout {
        require(singleScreenComponent?.isScreen() != false) { "When specifying a single screen component, it must be a screen component" }

        val safeLeft = insets.left
        val safeTop = insets.top
        val safeRight = insets.right
        val safeBottom = insets.bottom
        val safeWidth = width - safeLeft - safeRight
        val safeHeight = height - safeTop - safeBottom

        // Visual hierarchy: primary > secondary > utility
        val largeButtonsSize = screenUnitsConverter.dpToPixels(160f).toInt()  // D-pad, ABXY
        val lrButtonsSize = screenUnitsConverter.dpToPixels(54f).toInt()       // L/R (index-finger sized)
        val smallButtonsSize = screenUnitsConverter.dpToPixels(48f).toInt()    // SELECT, START
        val tinyButtonsSize = screenUnitsConverter.dpToPixels(42f).toInt()     // utility icons
        val spacing4dp = screenUnitsConverter.dpToPixels(4f).toInt()
        val margin8dp = screenUnitsConverter.dpToPixels(8f).toInt()

        // Screens: letterboxed at correct DS aspect ratio, centered in safe area
        val screenComponents = if (singleScreenComponent == null) {
            val screenArea = centerScreenIn(safeWidth, safeHeight)
            val topScreenView = Rect(screenArea.x + safeLeft, screenArea.y + safeTop, screenArea.width, screenArea.height)
            val bottomScreenView = Rect(screenArea.x + safeLeft, screenArea.y + safeTop, screenArea.width, screenArea.height)
            arrayOf(
                PositionedLayoutComponent(topScreenView, LayoutComponent.TOP_SCREEN),
                PositionedLayoutComponent(bottomScreenView, LayoutComponent.BOTTOM_SCREEN),
            )
        } else {
            val screenArea = centerScreenIn(safeWidth, safeHeight)
            val offsetScreen = Rect(screenArea.x + safeLeft, screenArea.y + safeTop, screenArea.width, screenArea.height)
            arrayOf(PositionedLayoutComponent(offsetScreen, singleScreenComponent))
        }

        // Ergonomic: thumb center at 68% of screen height (lower-middle, natural resting position)
        val primaryButtonTop = (height * 0.68f).toInt() - largeButtonsSize / 2

        // D-pad: left side, 8dp from safe edge — sits in the black letterbox bar beside the game
        val dpadView = Rect(safeLeft + margin8dp, primaryButtonTop, largeButtonsSize, largeButtonsSize)

        // ABXY: mirrored on the right side
        val buttonsView = Rect(width - safeRight - largeButtonsSize - margin8dp, primaryButtonTop, largeButtonsSize, largeButtonsSize)

        // L/R: top corners, smaller for index-finger tap, 8dp inset
        val buttonL = Rect(safeLeft + margin8dp, safeTop + margin8dp, lrButtonsSize, lrButtonsSize)
        val buttonR = Rect(width - safeRight - lrButtonsSize - margin8dp, safeTop + margin8dp, lrButtonsSize, lrButtonsSize)

        // SELECT below D-pad, START below ABXY — left/right thumb accessible without moving
        val secondaryButtonTop = height - safeBottom - smallButtonsSize - margin8dp
        val selectView = Rect(dpadView.x + largeButtonsSize / 2 - smallButtonsSize / 2, secondaryButtonTop, smallButtonsSize, smallButtonsSize)
        val startView = Rect(buttonsView.x + largeButtonsSize / 2 - smallButtonsSize / 2, secondaryButtonTop, smallButtonsSize, smallButtonsSize)

        // Utility buttons: top center, evenly spaced
        val utilityTotalWidth = tinyButtonsSize * 4 + spacing4dp * 3
        val utilityStartX = (width - utilityTotalWidth) / 2
        val utilityY = safeTop + margin8dp

        return ScreenLayout(
            listOf(
                *screenComponents,
                PositionedLayoutComponent(dpadView, LayoutComponent.DPAD),
                PositionedLayoutComponent(buttonsView, LayoutComponent.BUTTONS),
                PositionedLayoutComponent(buttonL, LayoutComponent.BUTTON_L),
                PositionedLayoutComponent(buttonR, LayoutComponent.BUTTON_R),
                PositionedLayoutComponent(selectView, LayoutComponent.BUTTON_SELECT),
                PositionedLayoutComponent(startView, LayoutComponent.BUTTON_START),
                PositionedLayoutComponent(Rect(utilityStartX, utilityY, tinyButtonsSize, tinyButtonsSize), LayoutComponent.BUTTON_HINGE),
                PositionedLayoutComponent(Rect(utilityStartX + tinyButtonsSize + spacing4dp, utilityY, tinyButtonsSize, tinyButtonsSize), LayoutComponent.BUTTON_TOGGLE_SOFT_INPUT),
                PositionedLayoutComponent(Rect(utilityStartX + (tinyButtonsSize + spacing4dp) * 2, utilityY, tinyButtonsSize, tinyButtonsSize), LayoutComponent.BUTTON_MICROPHONE_TOGGLE),
                PositionedLayoutComponent(Rect(utilityStartX + (tinyButtonsSize + spacing4dp) * 3, utilityY, tinyButtonsSize, tinyButtonsSize), LayoutComponent.BUTTON_FAST_FORWARD_TOGGLE),
            )
        )
    }

    /**
     * Creates a portrait layout that supports a horizontal fold. Screens are attached to either sides of the fold and as large as they can be to fit the screen.
     */
    private fun buildDefaultFoldingPortraitLayout(width: Int, height: Int, folds: List<ScreenFold>, insets: Insets): ScreenLayout {
        // Only one fold is supported for now
        val mainFold = folds.first()

        val safeLeft = insets.left
        val safeTop = insets.top
        val safeRight = insets.right
        val safeBottom = insets.bottom
        val safeWidth = width - safeLeft - safeRight

        val largeButtonsSize = screenUnitsConverter.dpToPixels(140f).toInt()
        val lrButtonsSize = screenUnitsConverter.dpToPixels(50f).toInt()
        val smallButtonsSize = screenUnitsConverter.dpToPixels(40f).toInt()
        val spacing4dp = screenUnitsConverter.dpToPixels(4f).toInt()

        var screenWidth = safeWidth
        var screenHeight = (safeWidth / consoleAspectRatio).toInt()
        val topHalfHeight = mainFold.foldBounds.y - safeTop
        val bottomHalfHeight = height - safeBottom - mainFold.foldBounds.bottom
        var screenMargin = 0
        if (screenHeight > topHalfHeight || screenHeight > bottomHalfHeight) {
            val limitingHeight = min(topHalfHeight, bottomHalfHeight)
            screenWidth = (limitingHeight * consoleAspectRatio).toInt()
            screenHeight = limitingHeight
            screenMargin = (safeWidth - screenWidth) / 2
        }

        val topScreenView = Rect(safeLeft + screenMargin, mainFold.foldBounds.y - screenHeight, screenWidth, screenHeight)
        val bottomScreenView = Rect(safeLeft + screenMargin, mainFold.foldBounds.bottom, screenWidth, screenHeight)
        val dpadView = Rect(safeLeft, height - safeBottom - largeButtonsSize, largeButtonsSize, largeButtonsSize)
        val buttonsView = Rect(width - safeRight - largeButtonsSize, height - safeBottom - largeButtonsSize, largeButtonsSize, largeButtonsSize)

        return ScreenLayout(
            listOf(
                PositionedLayoutComponent(topScreenView, LayoutComponent.TOP_SCREEN),
                PositionedLayoutComponent(bottomScreenView, LayoutComponent.BOTTOM_SCREEN),
                PositionedLayoutComponent(dpadView, LayoutComponent.DPAD),
                PositionedLayoutComponent(buttonsView, LayoutComponent.BUTTONS),
                PositionedLayoutComponent(Rect(safeLeft, mainFold.foldBounds.bottom, lrButtonsSize, lrButtonsSize), LayoutComponent.BUTTON_L),
                PositionedLayoutComponent(Rect(width - safeRight - lrButtonsSize, mainFold.foldBounds.bottom, lrButtonsSize, lrButtonsSize), LayoutComponent.BUTTON_R),
                PositionedLayoutComponent(Rect(width / 2 - smallButtonsSize - spacing4dp / 2, height - safeBottom - smallButtonsSize, smallButtonsSize, smallButtonsSize), LayoutComponent.BUTTON_SELECT),
                PositionedLayoutComponent(Rect(width / 2 + spacing4dp / 2, height - safeBottom - smallButtonsSize, smallButtonsSize, smallButtonsSize), LayoutComponent.BUTTON_START),
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
    private fun buildDefaultFoldingLandscapeLayout(width: Int, height: Int, folds: List<ScreenFold>, insets: Insets): ScreenLayout {
        // Only one fold is supported for now
        val mainFold = folds.first()

        val safeLeft = insets.left
        val safeTop = insets.top
        val safeRight = insets.right
        val safeBottom = insets.bottom
        val safeHeight = height - safeTop - safeBottom

        val largeButtonsSize = screenUnitsConverter.dpToPixels(140f).toInt()
        val lrButtonsSize = screenUnitsConverter.dpToPixels(50f).toInt()
        val smallButtonsSize = screenUnitsConverter.dpToPixels(40f).toInt()
        val spacing8dp = screenUnitsConverter.dpToPixels(8f).toInt()

        // Position to the left of the fold, attached to the fold. As big as the screen allows
        var topScreenWidth = mainFold.foldBounds.x - safeLeft
        var topScreenHeight = (topScreenWidth / consoleAspectRatio).toInt()
        if (topScreenHeight > safeHeight) {
            topScreenHeight = safeHeight
            topScreenWidth = (safeHeight * consoleAspectRatio).toInt()
        }

        // Position to the right of the fold, attached to the fold. As big as the screen allows
        var bottomScreenWidth = width - safeRight - mainFold.foldBounds.right
        var bottomScreenHeight = (bottomScreenWidth / consoleAspectRatio).toInt()
        if (bottomScreenHeight > safeHeight) {
            bottomScreenHeight = safeHeight
            bottomScreenWidth = (safeHeight * consoleAspectRatio).toInt()
        }

        // Check if the screens are small enough to be positioned under the L/R buttons
        val screenYPos = if (topScreenHeight < (safeHeight - lrButtonsSize - spacing8dp) && bottomScreenHeight < (safeHeight - lrButtonsSize - spacing8dp)) {
            safeTop + lrButtonsSize + spacing8dp
        } else {
            safeTop
        }

        val topScreenView = Rect(mainFold.foldBounds.x - topScreenWidth, screenYPos, topScreenWidth, topScreenHeight)
        val bottomScreenView = Rect(mainFold.foldBounds.right, screenYPos, bottomScreenWidth, bottomScreenHeight)
        val dpadView = Rect(safeLeft, height - safeBottom - largeButtonsSize, largeButtonsSize, largeButtonsSize)
        val buttonsView = Rect(width - safeRight - largeButtonsSize, height - safeBottom - largeButtonsSize, largeButtonsSize, largeButtonsSize)

        return ScreenLayout(
            listOf(
                PositionedLayoutComponent(topScreenView, LayoutComponent.TOP_SCREEN),
                PositionedLayoutComponent(bottomScreenView, LayoutComponent.BOTTOM_SCREEN),
                PositionedLayoutComponent(dpadView, LayoutComponent.DPAD),
                PositionedLayoutComponent(buttonsView, LayoutComponent.BUTTONS),
                PositionedLayoutComponent(Rect(safeLeft, safeTop, lrButtonsSize, lrButtonsSize), LayoutComponent.BUTTON_L),
                PositionedLayoutComponent(Rect(width - safeRight - lrButtonsSize, safeTop, lrButtonsSize, lrButtonsSize), LayoutComponent.BUTTON_R),
                PositionedLayoutComponent(Rect(mainFold.foldBounds.x - smallButtonsSize - spacing8dp, height - safeBottom - smallButtonsSize, smallButtonsSize, smallButtonsSize), LayoutComponent.BUTTON_SELECT),
                PositionedLayoutComponent(Rect(mainFold.foldBounds.right + spacing8dp, height - safeBottom - smallButtonsSize, smallButtonsSize, smallButtonsSize), LayoutComponent.BUTTON_START),
                PositionedLayoutComponent(Rect(mainFold.foldBounds.x - smallButtonsSize * 2 - spacing8dp * 2, safeTop, smallButtonsSize, smallButtonsSize), LayoutComponent.BUTTON_HINGE),
                PositionedLayoutComponent(Rect(mainFold.foldBounds.x - smallButtonsSize - spacing8dp, safeTop, smallButtonsSize, smallButtonsSize), LayoutComponent.BUTTON_TOGGLE_SOFT_INPUT),
                PositionedLayoutComponent(Rect(mainFold.foldBounds.right + smallButtonsSize + spacing8dp, safeTop, smallButtonsSize, smallButtonsSize), LayoutComponent.BUTTON_MICROPHONE_TOGGLE),
                PositionedLayoutComponent(Rect(mainFold.foldBounds.right + spacing8dp * 2, safeTop, smallButtonsSize, smallButtonsSize), LayoutComponent.BUTTON_FAST_FORWARD_TOGGLE),
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

    private fun getDisplaySafeInsets(display: LayoutDisplay): Insets {
        if (!display.isDefaultDisplay) {
            return Insets.Zero
        }

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            return Insets.Zero
        }

        val windowManager = context.getSystemService<WindowManager>() ?: return Insets.Zero

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val cutoutInsets = windowManager.currentWindowMetrics.windowInsets.getInsets(WindowInsets.Type.displayCutout())
            Insets(cutoutInsets.left, cutoutInsets.top, cutoutInsets.right, cutoutInsets.bottom)
        } else {
            @Suppress("DEPRECATION")
            val displayCutout = windowManager.defaultDisplay.cutout ?: return Insets.Zero
            Insets(displayCutout.safeInsetLeft, displayCutout.safeInsetTop, displayCutout.safeInsetRight, displayCutout.safeInsetBottom)
        }
    }
}