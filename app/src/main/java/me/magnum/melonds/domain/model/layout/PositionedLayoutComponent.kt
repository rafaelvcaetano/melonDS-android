package me.magnum.melonds.domain.model.layout

import me.magnum.melonds.domain.model.Rect

data class PositionedLayoutComponent(val rect: Rect, val component: LayoutComponent) {
    fun isScreen(): Boolean {
        return component.isScreen()
    }
}