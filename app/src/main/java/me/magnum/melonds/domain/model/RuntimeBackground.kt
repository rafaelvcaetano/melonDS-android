package me.magnum.melonds.domain.model

data class RuntimeBackground(val background: Background?, val mode: BackgroundMode) {

    companion object {
        val None = RuntimeBackground(null, BackgroundMode.STRETCH)
    }
}