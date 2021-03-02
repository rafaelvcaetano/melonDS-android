package me.magnum.melonds.ui.common

import android.content.Context
import android.content.res.Configuration
import android.util.AttributeSet
import android.widget.FrameLayout
import dagger.hilt.android.AndroidEntryPoint
import me.magnum.melonds.domain.model.LayoutComponent
import me.magnum.melonds.domain.model.LayoutConfiguration
import me.magnum.melonds.domain.model.PositionedLayoutComponent
import me.magnum.melonds.domain.model.UILayout
import me.magnum.melonds.impl.ScreenUnitsConverter
import javax.inject.Inject

@AndroidEntryPoint
open class LayoutView(context: Context, attrs: AttributeSet?) : FrameLayout(context, attrs) {
    @Inject
    lateinit var screenUnitsConverter: ScreenUnitsConverter

    protected val layoutComponentViewBuilderFactory = LayoutComponentViewBuilderFactory()
    protected val views = mutableMapOf<LayoutComponent, LayoutComponentView>()

    fun instantiateLayout(layoutConfiguration: LayoutConfiguration) {
        views.clear()
        removeAllViews()

        if (resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT) {
            loadLayout(layoutConfiguration.portraitLayout)
        } else {
            loadLayout(layoutConfiguration.landscapeLayout)
        }
    }

    fun getInstantiatedComponents(): List<LayoutComponent> {
        return views.keys.toList()
    }

    private fun loadLayout(layout: UILayout) {
        layout.components.forEach {
            views[it.component] = addPositionedLayoutComponent(it)
        }
    }

    protected fun addPositionedLayoutComponent(layoutComponent: PositionedLayoutComponent): LayoutComponentView {
        val viewBuilder = layoutComponentViewBuilderFactory.getLayoutComponentViewBuilder(layoutComponent.component)
        val view = viewBuilder.build(context).apply {
            alpha = 0.5f
        }

        val viewParams = LayoutParams(layoutComponent.rect.width, layoutComponent.rect.height).apply {
            leftMargin = layoutComponent.rect.x
            topMargin = layoutComponent.rect.y
        }

        val viewLayoutComponent = LayoutComponentView(view, viewBuilder.getAspectRatio(), layoutComponent.component)
        if (layoutComponent.isScreen()) {
            // Screens should be below other views
            addView(view, 0, viewParams)
        } else {
            addView(view, viewParams)
        }

        onLayoutComponentViewAdded(viewLayoutComponent)
        return viewLayoutComponent
    }

    protected open fun onLayoutComponentViewAdded(layoutComponentView: LayoutComponentView) {
    }
}