package me.magnum.melonds.migrations.legacy.layout

import com.google.gson.annotations.SerializedName

/**
 * PositionedLayoutComponent model used until app version 25.
 */
data class PositionedLayoutComponent25(
    @SerializedName("a")
    val rect: Rect25,
    @SerializedName("b")
    val component: String,
)