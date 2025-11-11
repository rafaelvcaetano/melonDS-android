package me.magnum.melonds.ui.common

import android.content.Context
import android.util.AttributeSet
import android.widget.FrameLayout
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import me.magnum.melonds.domain.model.layout.LayoutComponent
import me.magnum.melonds.domain.model.layout.PositionedLayoutComponent
import me.magnum.melonds.domain.model.layout.UILayout
import me.magnum.melonds.impl.ScreenUnitsConverter

open class LayoutView(context: Context, attrs: AttributeSet?) : FrameLayout(context, attrs) {
    protected val screenUnitsConverter: ScreenUnitsConverter by lazy(LazyThreadSafetyMode.NONE) {
        val appContext = context.applicationContext ?: context
        EntryPointAccessors.fromApplication(appContext, LayoutViewDependencies::class.java).screenUnitsConverter()
    }

    protected lateinit var viewBuilderFactory: LayoutComponentViewBuilderFactory
    protected val views = mutableMapOf<LayoutComponent, LayoutComponentView>()

    init {
        layoutDirection = LAYOUT_DIRECTION_LTR
    }

    fun setLayoutComponentViewBuilderFactory(factory: LayoutComponentViewBuilderFactory) {
        viewBuilderFactory = factory
    }

    open fun instantiateLayout(layoutConfiguration: UILayout) {
        destroyLayout()
        loadLayout(layoutConfiguration)
    }

    fun destroyLayout() {
        views.clear()
        removeAllViews()
    }

    fun getInstantiatedComponents(): List<LayoutComponent> {
        return views.keys.toList()
    }

    fun getLayoutComponentViews(): List<LayoutComponentView> {
        return views.values.toList()
    }

    fun getLayoutComponentView(layoutComponent: LayoutComponent): LayoutComponentView? {
        return views[layoutComponent]
    }

    private fun loadLayout(layout: UILayout) {
        val components = layout.components ?: return

        val screens = components.filter { it.isScreen() }.sortedBy { if (it.onTop) 0 else 1 }
        val others = components.filterNot { it.isScreen() }

        (screens + others).forEach {
            views[it.component] = addPositionedLayoutComponent(it)
        }
    }

    protected fun addPositionedLayoutComponent(layoutComponent: PositionedLayoutComponent): LayoutComponentView {
        val viewBuilder = viewBuilderFactory.getLayoutComponentViewBuilder(layoutComponent.component)
        val view = viewBuilder.build(context)

        val viewParams = LayoutParams(layoutComponent.rect.width, layoutComponent.rect.height).apply {
            leftMargin = layoutComponent.rect.x
            topMargin = layoutComponent.rect.y
        }

        val viewLayoutComponent = LayoutComponentView(view, viewBuilder.getAspectRatio(), layoutComponent.component).apply {
            baseAlpha = layoutComponent.alpha
            setHighlighted(false)
            onTop = layoutComponent.onTop
        }

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

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface LayoutViewDependencies {
        fun screenUnitsConverter(): ScreenUnitsConverter
    }
}
