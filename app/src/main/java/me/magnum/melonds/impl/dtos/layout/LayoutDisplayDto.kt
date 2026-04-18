package me.magnum.melonds.impl.dtos.layout

import com.google.gson.annotations.SerializedName
import me.magnum.melonds.domain.model.layout.LayoutDisplay
import me.magnum.melonds.utils.enumValueOfIgnoreCase

data class LayoutDisplayDto(
    @SerializedName("id") val id: Int,
    @SerializedName("type") val type: String,
    @SerializedName("width") val width: Int,
    @SerializedName("height") val height: Int,
) {

    companion object {
        fun fromModel(model: LayoutDisplay): LayoutDisplayDto {
            return LayoutDisplayDto(
                id = model.id,
                type = model.type.name,
                width = model.width,
                height = model.height,
            )
        }
    }

    fun toModel(): LayoutDisplay {
        return LayoutDisplay(
            id = id,
            type = enumValueOfIgnoreCase(type),
            width = width,
            height = height,
        )
    }
}