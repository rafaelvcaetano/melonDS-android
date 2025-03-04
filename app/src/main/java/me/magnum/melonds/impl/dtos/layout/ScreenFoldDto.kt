package me.magnum.melonds.impl.dtos.layout

import me.magnum.melonds.domain.model.layout.ScreenFold
import me.magnum.melonds.utils.enumValueOfIgnoreCase

data class ScreenFoldDto(
    val orientation: String,
    val type: String,
    val foldBounds: RectDto,
) {

    fun toModel(): ScreenFold {
        return ScreenFold(
            enumValueOfIgnoreCase(orientation),
            enumValueOfIgnoreCase(type),
            foldBounds.toModel(),
        )
    }

    companion object {
        fun fromModel(fold: ScreenFold): ScreenFoldDto {
            return ScreenFoldDto(
                fold.orientation.name,
                fold.type.name,
                RectDto.fromModel(fold.foldBounds),
            )
        }
    }
}