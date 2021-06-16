package me.magnum.melonds.impl

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.view.WindowManager
import androidx.core.content.getSystemService
import me.magnum.melonds.domain.model.*
import kotlin.math.roundToInt


class DefaultLayoutProvider(private val context: Context, private val screenUnitsConverter: ScreenUnitsConverter) {
    companion object {
        private const val DS_ASPECT_RATIO = 256f / 192f
    }

    val defaultLayout by lazy {
        buildDefaultLayout()
    }

    private fun buildDefaultLayout(): LayoutConfiguration {
        val windowService = context.getSystemService<WindowManager>()!!
        val display = windowService.defaultDisplay
        val point = android.graphics.Point()
        display.getRealSize(point)

        var screenWidth: Int
        var screenHeight: Int
        if (context.resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT) {
            screenWidth = point.x
            screenHeight = point.y

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                display.cutout?.let {
                    screenWidth -= it.safeInsetLeft + it.safeInsetRight
                    screenHeight -= it.safeInsetTop + it.safeInsetBottom
                }
            }
        } else {
            screenWidth = point.y
            screenHeight = point.x

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                display.cutout?.let {
                    screenWidth -= it.safeInsetTop + it.safeInsetBottom
                    screenHeight -= it.safeInsetLeft + it.safeInsetRight
                }
            }
        }

        return constructLayout(screenWidth, screenHeight)
    }

    private fun constructLayout(portraitWidth: Int, portraitHeight: Int): LayoutConfiguration {
        return LayoutConfiguration.newCustom(
                buildDefaultPortraitLayout(portraitWidth, portraitHeight),
                buildDefaultLandscapeLayout(portraitHeight, portraitWidth)
        )
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
            screenWidth = (height * DS_ASPECT_RATIO).toInt()
            screenHeight = height
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
                        PositionedLayoutComponent(Rect(width / 2 - (smallButtonsSize * 1.5).toInt() - spacing4dp * 2, screenHeight, smallButtonsSize, smallButtonsSize), LayoutComponent.BUTTON_HINGE),
                        PositionedLayoutComponent(Rect(width / 2 - (smallButtonsSize * 0.5).toInt(), screenHeight, smallButtonsSize, smallButtonsSize), LayoutComponent.BUTTON_TOGGLE_SOFT_INPUT),
                        PositionedLayoutComponent(Rect(width / 2 + (smallButtonsSize * 0.5).toInt() + spacing4dp * 2, screenHeight, smallButtonsSize, smallButtonsSize), LayoutComponent.BUTTON_FAST_FORWARD_TOGGLE),
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
                        PositionedLayoutComponent(Rect(width / 2 - (smallButtonsSize * 1.5).toInt() - spacing4dp * 2, 0, smallButtonsSize, smallButtonsSize), LayoutComponent.BUTTON_HINGE),
                        PositionedLayoutComponent(Rect(width / 2 - (smallButtonsSize * 0.5).toInt(), 0, smallButtonsSize, smallButtonsSize), LayoutComponent.BUTTON_TOGGLE_SOFT_INPUT),
                        PositionedLayoutComponent(Rect(width / 2 + (smallButtonsSize * 0.5).toInt() + spacing4dp * 2, 0, smallButtonsSize, smallButtonsSize), LayoutComponent.BUTTON_FAST_FORWARD_TOGGLE),
                )
        )
    }
}