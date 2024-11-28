package me.magnum.melonds.domain.model

import me.magnum.melonds.domain.model.layout.BackgroundMode

data class RuntimeBackground(val background: Background?, val mode: BackgroundMode) {

    companion object {
        val None = RuntimeBackground(null, BackgroundMode.STRETCH)
    }
}