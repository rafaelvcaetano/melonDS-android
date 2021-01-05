package me.magnum.melonds.domain.model

import android.net.Uri

data class RomConfig(var runtimeConsoleType: RuntimeConsoleType = RuntimeConsoleType.DEFAULT, private var loadGbaCart: Boolean = false, var gbaCartPath: Uri? = null, var gbaSavePath: Uri? = null) {
    fun loadGbaCart(): Boolean {
        return loadGbaCart
    }

    fun setLoadGbaCart(loadGbaCart: Boolean) {
        this.loadGbaCart = loadGbaCart
    }
}