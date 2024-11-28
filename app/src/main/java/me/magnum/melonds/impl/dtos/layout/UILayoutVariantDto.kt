package me.magnum.melonds.impl.dtos.layout

import com.google.gson.annotations.SerializedName
import me.magnum.melonds.domain.model.layout.UILayoutVariant

data class UILayoutVariantDto(
    @SerializedName("uiSize")
    val uiSize: PointDto,
    @SerializedName("orientation")
    val orientation: String,
    @SerializedName("folds")
    val folds: List<ScreenFoldDto>,
) {

    fun toModel(): UILayoutVariant {
        return UILayoutVariant(
            uiSize.toModel(),
            enumValueOf(orientation),
            folds.map {
                it.toModel()
            },
        )
    }

    companion object {
        fun fromModel(uiLayoutVariant: UILayoutVariant): UILayoutVariantDto {
            return UILayoutVariantDto(
                PointDto.fromModel(uiLayoutVariant.uiSize),
                uiLayoutVariant.orientation.name,
                uiLayoutVariant.folds.map {
                    ScreenFoldDto.fromModel(it)
                },
            )
        }
    }
}