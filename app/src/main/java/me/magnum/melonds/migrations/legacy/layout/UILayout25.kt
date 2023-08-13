package me.magnum.melonds.migrations.legacy.layout

import com.google.gson.annotations.SerializedName

/**
 * UILayout model used until app version 25.
 */
data class UILayout25(
    @SerializedName("a")
    val backgroundId: String?,
    @SerializedName("b")
    val backgroundMode: String,
    @SerializedName("c")
    val components: List<PositionedLayoutComponent25>,
)