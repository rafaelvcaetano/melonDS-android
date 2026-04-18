package me.magnum.melonds.domain.model.layout

data class LayoutDisplay(
    val id: Int,
    val type: Type,
    val width: Int,
    val height: Int,
) {

    companion object {
        const val DEFAULT_DISPLAY_ID = 0
    }

    val isDefaultDisplay get() = id == DEFAULT_DISPLAY_ID

    enum class Type {
        BUILT_IN,
        EXTERNAL,
    }
}