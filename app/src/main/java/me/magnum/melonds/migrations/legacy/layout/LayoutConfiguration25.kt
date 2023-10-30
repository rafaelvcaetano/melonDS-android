package me.magnum.melonds.migrations.legacy.layout

import com.google.gson.annotations.SerializedName

/**
 * LayoutConfiguration model used until app version 25.
 */
data class LayoutConfiguration25(
    @SerializedName("a")
    val id: String?,
    @SerializedName("b")
    val name: String?,
    @SerializedName("c")
    val type: String,
    @SerializedName("d")
    val orientation: String,
    @SerializedName("e")
    val useCustomOpacity: Boolean,
    @SerializedName("f")
    val opacity: Int,
    @SerializedName("g")
    val portraitLayout: UILayout25,
    @SerializedName("h")
    val landscapeLayout: UILayout25,
)