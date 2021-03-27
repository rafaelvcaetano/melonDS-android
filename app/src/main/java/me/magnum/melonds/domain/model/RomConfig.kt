package me.magnum.melonds.domain.model

import android.net.Uri
import java.util.*

data class RomConfig(
        var runtimeConsoleType: RuntimeConsoleType = RuntimeConsoleType.DEFAULT,
        var runtimeMicSource: RuntimeMicSource = RuntimeMicSource.DEFAULT,
        var layoutId: UUID? = null,
        private var loadGbaCart: Boolean = false,
        var gbaCartPath: Uri? = null,
        var gbaSavePath: Uri? = null
) {
    fun loadGbaCart(): Boolean {
        return loadGbaCart && gbaCartPath != null
    }

    fun setLoadGbaCart(loadGbaCart: Boolean) {
        this.loadGbaCart = loadGbaCart
    }
}