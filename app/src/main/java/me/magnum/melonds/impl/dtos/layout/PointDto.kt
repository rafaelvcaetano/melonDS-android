package me.magnum.melonds.impl.dtos.layout

import com.google.gson.annotations.SerializedName
import me.magnum.melonds.domain.model.Point

data class PointDto(
    @SerializedName("x")
    val x: Int,
    @SerializedName("y")
    val y: Int,
) {

    fun toModel(): Point {
        return Point(x, y)
    }

    companion object {
        fun fromModel(point: Point): PointDto {
            return PointDto(point.x, point.y)
        }
    }
}