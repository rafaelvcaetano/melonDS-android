package me.magnum.melonds.domain.model.layout

import me.magnum.melonds.domain.model.Input

enum class LayoutComponent(val matchingInputs: List<Input>) {
    TOP_SCREEN(emptyList()),
    BOTTOM_SCREEN(emptyList()),
    DPAD(listOf(Input.UP, Input.DOWN, Input.LEFT, Input.RIGHT)),
    BUTTONS(listOf(Input.A, Input.B, Input.X, Input.Y)),
    BUTTON_START(listOf(Input.START)),
    BUTTON_SELECT(listOf(Input.SELECT)),
    BUTTON_L(listOf(Input.L)),
    BUTTON_R(listOf(Input.R)),
    BUTTON_HINGE(listOf(Input.HINGE)),
    BUTTON_FAST_FORWARD_TOGGLE(listOf(Input.FAST_FORWARD)),
    BUTTON_TOGGLE_SOFT_INPUT(listOf(Input.TOGGLE_SOFT_INPUT)),
    BUTTON_RESET(listOf(Input.RESET)),
    BUTTON_PAUSE(listOf(Input.PAUSE)),
    BUTTON_SWAP_SCREENS(listOf(Input.SWAP_SCREENS)),
    BUTTON_QUICK_SAVE(listOf(Input.QUICK_SAVE)),
    BUTTON_QUICK_LOAD(listOf(Input.QUICK_LOAD)),
    BUTTON_REWIND(listOf(Input.REWIND)),
    BUTTON_MICROPHONE_TOGGLE(listOf(Input.MICROPHONE));

    fun isScreen(): Boolean {
        return this == TOP_SCREEN || this == BOTTOM_SCREEN
    }
}