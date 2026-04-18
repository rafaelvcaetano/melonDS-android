package me.magnum.melonds.impl.dtos.layout

import com.google.gson.annotations.SerializedName
import me.magnum.melonds.domain.model.layout.LayoutDisplayPair

data class LayoutDisplayPairDto(
    @SerializedName("mainScreenDisplay")
    val mainScreenDisplay: LayoutDisplayDto,
    @SerializedName("secondaryScreenDisplay")
    val secondaryScreenDisplay: LayoutDisplayDto?,
) {

    fun toModel(): LayoutDisplayPair {
        return LayoutDisplayPair(
            mainScreenDisplay = mainScreenDisplay.toModel(),
            secondaryScreenDisplay = secondaryScreenDisplay?.toModel(),
        )
    }

    companion object {
        fun fromModel(layoutDisplayPair: LayoutDisplayPair): LayoutDisplayPairDto {
            return LayoutDisplayPairDto(
                mainScreenDisplay = LayoutDisplayDto.fromModel(layoutDisplayPair.mainScreenDisplay),
                secondaryScreenDisplay = layoutDisplayPair.secondaryScreenDisplay?.let { LayoutDisplayDto.fromModel(it) },
            )
        }
    }
}