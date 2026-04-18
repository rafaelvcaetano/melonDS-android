package me.magnum.melonds.migrations.legacy.layout

import com.google.gson.annotations.SerializedName
import me.magnum.melonds.impl.dtos.layout.PointDto
import me.magnum.melonds.impl.dtos.layout.ScreenFoldDto

data class UILayoutVariantDto35(
    @SerializedName("uiSize")
    val uiSize: PointDto,
    @SerializedName("orientation")
    val orientation: String,
    @SerializedName("folds")
    val folds: List<ScreenFoldDto>,
)