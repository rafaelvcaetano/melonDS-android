package me.magnum.melonds.ui.common

import me.magnum.melonds.domain.model.LayoutComponent
import me.magnum.melonds.ui.common.componentbuilders.*

interface LayoutComponentViewBuilderFactory {
    fun getLayoutComponentViewBuilder(layoutComponent: LayoutComponent): LayoutComponentViewBuilder
}