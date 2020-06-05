package me.magnum.melonds.model

data class RomConfig(private var loadGbaCart: Boolean = false, var gbaCartPath: String? = null, var gbaSavePath: String? = null) {
    fun loadGbaCart(): Boolean {
        return loadGbaCart
    }

    fun setLoadGbaCart(loadGbaCart: Boolean) {
        this.loadGbaCart = loadGbaCart
    }
}