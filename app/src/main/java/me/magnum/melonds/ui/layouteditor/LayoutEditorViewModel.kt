package me.magnum.melonds.ui.layouteditor

import androidx.hilt.lifecycle.ViewModelInject
import androidx.lifecycle.ViewModel
import me.magnum.melonds.domain.model.*
import me.magnum.melonds.domain.repositories.LayoutsRepository
import me.magnum.melonds.impl.ScreenUnitsConverter

class LayoutEditorViewModel @ViewModelInject constructor(private val layoutsRepository: LayoutsRepository, private val screenUnitsConverter: ScreenUnitsConverter) : ViewModel() {
    enum class LayoutOrientation {
        PORTRAIT,
        LANDSCAPE
    }

    companion object {
        private const val DS_ASPECT_RATIO = 256f / 192f
    }

    private var currentLayoutConfiguration: LayoutConfiguration? = null
    private var initialLayoutConfiguration: LayoutConfiguration? = null

    fun getCurrentLayoutConfiguration(): LayoutConfiguration? {
        return currentLayoutConfiguration
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

    fun setCurrentLayoutConfiguration(layoutConfiguration: LayoutConfiguration) {
        currentLayoutConfiguration = layoutConfiguration
        initialLayoutConfiguration = layoutConfiguration.copy()
    }

    fun getInitialLayoutConfiguration(): LayoutConfiguration? {
        return initialLayoutConfiguration
    }

    fun saveLayoutToCurrentConfiguration(layout: UILayout, orientation: LayoutOrientation) {
        currentLayoutConfiguration?.let {
            currentLayoutConfiguration = when (orientation) {
                LayoutOrientation.PORTRAIT -> it.copy(portraitLayout = layout)
                LayoutOrientation.LANDSCAPE -> it.copy(landscapeLayout = layout)
            }
        }
    }

    fun getDefaultLayoutConfiguration(portraitWidth: Int, portraitHeight: Int): LayoutConfiguration {
        return LayoutConfiguration.new(
                buildDefaultPortraitLayout(portraitWidth, portraitHeight),
                buildDefaultLandscapeLayout(portraitHeight, portraitWidth)
        )
    }

    fun isCurrentLayoutNew(): Boolean {
        return currentLayoutConfiguration?.id == null
    }

    private fun buildDefaultPortraitLayout(width: Int, height: Int): UILayout {
        val largeButtonsSize = screenUnitsConverter.dpToPixels(140f).toInt()
        val lrButtonsSize = screenUnitsConverter.dpToPixels(60f).toInt()
        val smallButtonsSize = screenUnitsConverter.dpToPixels(40f).toInt()
        val spacing4dp = screenUnitsConverter.dpToPixels(4f).toInt()

        val screenHeight = (width / DS_ASPECT_RATIO).toInt()
        val topScreenView = Rect(0, 0, width, screenHeight)
        val bottomScreenView = Rect(0, screenHeight, width, screenHeight)
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
        val topScreenWidth = (height * DS_ASPECT_RATIO).toInt()
        val topScreenView = Rect(0, 0, topScreenWidth, height)
        val bottomScreenWidth = width - topScreenWidth
        val bottomScreenView = Rect(topScreenWidth, 0, bottomScreenWidth, (bottomScreenWidth / DS_ASPECT_RATIO).toInt())
        val dpadView = Rect(0, height - 400, 400, 400)
        val buttonsView = Rect(width - 400, height - 400, 400, 400)

        return UILayout(
                listOf(
                        PositionedLayoutComponent(topScreenView, LayoutComponent.TOP_SCREEN),
                        PositionedLayoutComponent(bottomScreenView, LayoutComponent.BOTTOM_SCREEN),
                        PositionedLayoutComponent(dpadView, LayoutComponent.DPAD),
                        PositionedLayoutComponent(buttonsView, LayoutComponent.BUTTONS),
                        PositionedLayoutComponent(Rect(0, 0, 100, 100), LayoutComponent.BUTTON_L),
                        PositionedLayoutComponent(Rect(width - 100, 0, 100, 100), LayoutComponent.BUTTON_R),
                        PositionedLayoutComponent(Rect(width / 2 - 100, height - 100, 100, 100), LayoutComponent.BUTTON_SELECT),
                        PositionedLayoutComponent(Rect(width / 2, height - 100, 100, 100), LayoutComponent.BUTTON_START),
                )
        )
    }
}