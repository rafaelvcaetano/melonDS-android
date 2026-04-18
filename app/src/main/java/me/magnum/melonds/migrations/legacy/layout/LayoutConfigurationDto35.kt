package me.magnum.melonds.migrations.legacy.layout

import com.google.gson.annotations.SerializedName

data class LayoutConfigurationDto35(
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
    @SerializedName("layoutVariants")
    val layoutVariants: List<LayoutEntryDto35>,
    @SerializedName("target")
    val target: String? = null,
) {

    data class LayoutEntryDto35(
        @SerializedName("variant")
        val variant: UILayoutVariantDto35,
        @SerializedName("layout")
        val layout: UILayoutDto35,
    )
}