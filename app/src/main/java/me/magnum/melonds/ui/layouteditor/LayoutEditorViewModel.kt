package me.magnum.melonds.ui.layouteditor

import androidx.hilt.Assisted
import androidx.hilt.lifecycle.ViewModelInject
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import me.magnum.melonds.domain.model.LayoutConfiguration
import me.magnum.melonds.domain.model.UILayout
import me.magnum.melonds.domain.repositories.LayoutsRepository
import me.magnum.melonds.impl.DefaultLayoutBuilder
import java.util.*

class LayoutEditorViewModel @ViewModelInject constructor(
        private val layoutsRepository: LayoutsRepository,
        private val defaultLayoutBuilder: DefaultLayoutBuilder,
        @Assisted private val savedStateHandle: SavedStateHandle
) : ViewModel() {

    enum class LayoutOrientation {
        PORTRAIT,
        LANDSCAPE
    }

    private var currentLayoutConfiguration: LayoutConfiguration? = null
    private var initialLayoutConfiguration: LayoutConfiguration? = null

    init {
        val layoutId = savedStateHandle.get<String?>(LayoutEditorActivity.KEY_LAYOUT_ID)?.let { UUID.fromString(it) }
        if (layoutId != null) {
            // Good? No. Does it work? Hell yeah!
            val layout = layoutsRepository.getLayout(layoutId).blockingGet()
            initialLayoutConfiguration = layout
            currentLayoutConfiguration = layout
        }
    }

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

    fun getDefaultLayoutConfiguration(): LayoutConfiguration {
        return defaultLayoutBuilder.getDefaultLayout()
    }

    fun isCurrentLayoutNew(): Boolean {
        return currentLayoutConfiguration?.id == null
    }
}