package me.magnum.melonds.domain.model

enum class BackgroundMode {
    STRETCH,
    FIT_CENTER,
    FIT_TOP,
    FIT_BOTTOM,
    FIT_LEFT,
    FIT_RIGHT;

    companion object {
        val PORTRAIT_MODES = arrayOf(STRETCH, FIT_CENTER, FIT_TOP, FIT_BOTTOM)
        val LANDSCAPE_MODES = arrayOf(STRETCH, FIT_CENTER, FIT_LEFT, FIT_RIGHT)
    }
}