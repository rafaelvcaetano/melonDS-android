package me.magnum.melonds.migrations.legacy.layout

import com.google.gson.annotations.SerializedName
import me.magnum.melonds.impl.dtos.layout.PositionedLayoutComponentDto

/**
 * UILayoutDto used until app version 35.
 */
data class UILayoutDto35(
    @SerializedName("backgroundId")
    val backgroundId: String?,
    @SerializedName("backgroundMode")
    val backgroundMode: String,
    @SerializedName("components")
    val components: List<PositionedLayoutComponentDto>?,
)
