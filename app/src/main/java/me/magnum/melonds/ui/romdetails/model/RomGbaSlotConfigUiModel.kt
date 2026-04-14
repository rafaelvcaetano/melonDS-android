package me.magnum.melonds.ui.romdetails.model

data class RomGbaSlotConfigUiModel(
    val type: Type = Type.None,
    val gbaRomPath: String? = null,
    val gbaSavePath: String? = null,
) {

    enum class Type {
        None, GbaRom, RumblePak, MemoryExpansion
    }
}
