package me.magnum.melonds.domain.model

class PositionedLayoutComponent(val rect: Rect, val component: LayoutComponent) {
    fun isScreen(): Boolean {
        return component == LayoutComponent.TOP_SCREEN || component == LayoutComponent.BOTTOM_SCREEN
    }
}