package me.magnum.melonds.ui.common

import me.magnum.melonds.domain.model.LayoutComponent

interface LayoutComponentViewBuilderFactory {
    fun getLayoutComponentViewBuilder(layoutComponent: LayoutComponent): LayoutComponentViewBuilder
}