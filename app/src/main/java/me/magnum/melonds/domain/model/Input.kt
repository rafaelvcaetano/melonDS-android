package me.magnum.melonds.domain.model

/**
 * Input representation that is assigned to the given key code. If the input does not represent a
 * system input (i.e., it represents additional functionality offered by the emulator), a key code
 * of -1 must be used.
 *
 * @param keyCode The key code that the input represents in the system or -1 if it is not assigned
 * to any system input
 */
enum class Input(val keyCode: Int) {
    A(0),
    B(1),
    SELECT(2),
    START(3),
    RIGHT(4),
    LEFT(5),
    UP(6),
    DOWN(7),
    R(8),
    L(9),
    X(10),
    Y(11),
    DEBUG(16 + 3),
    TOUCHSCREEN(16 + 6),
    HINGE(16 + 7),
    PAUSE(-1),
    FAST_FORWARD(-1),
    MICROPHONE(-1),
    RESET(-1),
    TOGGLE_SOFT_INPUT(-1),
    SWAP_SCREENS(-1),
    QUICK_SAVE(-1),
    QUICK_LOAD(-1),
    REWIND(-1),
    REFRESH_EXTERNAL_SCREEN(-1);

    val isSystemInput: Boolean
        get() = keyCode != -1

    companion object {
        val SYSTEM_BUTTONS = listOf(A, B, X, Y, L, R, START, SELECT, LEFT, RIGHT, UP, DOWN)
    }
}