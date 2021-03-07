package me.magnum.melonds.domain.model

class PositionedLayoutComponent(val rect: Rect, val component: LayoutComponent) {
    fun isScreen(): Boolean {
        return component.isScreen()
    }
}