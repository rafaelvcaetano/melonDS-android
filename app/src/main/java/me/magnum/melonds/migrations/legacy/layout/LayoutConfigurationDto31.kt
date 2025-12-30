package me.magnum.melonds.migrations.legacy.layout

import com.google.gson.annotations.SerializedName

data class LayoutConfigurationDto31(
    @SerializedName("id")
    val id: String?,
    @SerializedName("name")
    val name: String?,
    @SerializedName("type")
    val type: String,
    @SerializedName("orientation")
    val orientation: String,
    @SerializedName("useCustomOpacity")
    val useCustomOpacity: Boolean,
    @SerializedName("opacity")
    val opacity: Int,
    @SerializedName("portraitLayout")
    val portraitLayout: UILayoutDto35,
    @SerializedName("landscapeLayout")
    val landscapeLayout: UILayoutDto35
)