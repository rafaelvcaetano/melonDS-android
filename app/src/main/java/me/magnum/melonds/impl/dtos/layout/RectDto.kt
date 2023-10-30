package me.magnum.melonds.impl.dtos.layout

import com.google.gson.annotations.SerializedName
import me.magnum.melonds.domain.model.Rect

data class RectDto(
    @SerializedName("x")
    val x: Int,
    @SerializedName("y")
    val y: Int,
    @SerializedName("width")
    val width: Int,
    @SerializedName("height")
    val height: Int
) {

    companion object {
        fun fromModel(rect: Rect): RectDto {
            return RectDto(rect.x, rect.y, rect.width, rect.height)
        }
    }

    fun toModel(): Rect {
        return Rect(x, y, width, height)
    }
}