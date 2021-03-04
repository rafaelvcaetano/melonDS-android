package me.magnum.melonds.ui.layouteditor

import me.magnum.melonds.domain.model.LayoutComponent
import me.magnum.melonds.ui.common.LayoutComponentViewBuilder
import me.magnum.melonds.ui.common.LayoutComponentViewBuilderFactory
import me.magnum.melonds.ui.common.componentbuilders.*

class EditorLayoutComponentViewBuilderFactory : LayoutComponentViewBuilderFactory {
    private val layoutComponentViewBuilderCache = mutableMapOf<LayoutComponent, LayoutComponentViewBuilder>()

    override fun getLayoutComponentViewBuilder(layoutComponent: LayoutComponent): LayoutComponentViewBuilder {
        return layoutComponentViewBuilderCache.getOrElse(layoutComponent) {
            val builder = when (layoutComponent) {
                LayoutComponent.TOP_SCREEN -> TopScreenLayoutComponentViewBuilder()
                LayoutComponent.BOTTOM_SCREEN -> BottomScreenLayoutComponentViewBuilder()
                LayoutComponent.DPAD -> EditorBackgroundLayoutComponentViewBuilder(DpadLayoutComponentViewBuilder())
                LayoutComponent.BUTTONS -> EditorBackgroundLayoutComponentViewBuilder(ButtonsLayoutComponentViewBuilder())
                else -> EditorBackgroundLayoutComponentViewBuilder(SingleButtonLayoutComponentViewBuilder(layoutComponent))
            }

            layoutComponentViewBuilderCache[layoutComponent] = builder
            builder
        }
    }
}