package me.magnum.melonds.migrations.legacy.layout

import com.google.gson.annotations.SerializedName

/**
 * Rect model used until app version 25.
 */
data class Rect25(
    @SerializedName("a")
    val x: Int,
    @SerializedName("b")
    val y: Int,
    @SerializedName("c")
    val width: Int,
    @SerializedName("d")
    val height: Int,
)