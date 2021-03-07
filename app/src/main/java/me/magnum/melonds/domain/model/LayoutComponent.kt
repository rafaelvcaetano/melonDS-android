package me.magnum.melonds.domain.model

enum class LayoutComponent {
    TOP_SCREEN,
    BOTTOM_SCREEN,
    DPAD,
    BUTTONS,
    BUTTON_START,
    BUTTON_SELECT,
    BUTTON_L,
    BUTTON_R,
    BUTTON_HINGE,
    BUTTON_FAST_FORWARD_TOGGLE,
    BUTTON_TOGGLE_SOFT_INPUT,
    BUTTON_RESET,
    BUTTON_PAUSE,
    BUTTON_SWAP_SCREENS;

    fun isScreen(): Boolean {
        return this == TOP_SCREEN || this == BOTTOM_SCREEN
    }
}