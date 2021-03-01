package me.magnum.melonds.ui.layouteditor

import me.magnum.melonds.domain.model.LayoutComponent
import me.magnum.melonds.ui.layouteditor.componentbuilders.*

class LayoutComponentViewBuilderFactory {
    private val layoutComponentViewBuilderCache = mutableMapOf<LayoutComponent, LayoutComponentViewBuilder>()

    fun getLayoutComponentViewBuilder(layoutComponent: LayoutComponent): LayoutComponentViewBuilder {
        return layoutComponentViewBuilderCache.getOrElse(layoutComponent) {
            val builder = when (layoutComponent) {
                LayoutComponent.TOP_SCREEN -> TopScreenLayoutComponentViewBuilder()
                LayoutComponent.BOTTOM_SCREEN -> BottomScreenLayoutComponentViewBuilder()
                LayoutComponent.DPAD -> DpadLayoutComponentViewBuilder()
                LayoutComponent.BUTTONS -> ButtonsLayoutComponentViewBuilder()
                else -> SingleButtonLayoutComponentViewBuilder(layoutComponent)
            }

            layoutComponentViewBuilderCache[layoutComponent] = builder
            builder
        }
    }
}