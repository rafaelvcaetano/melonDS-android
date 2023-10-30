package me.magnum.melonds.impl.dtos.layout

import com.google.gson.annotations.SerializedName
import me.magnum.melonds.domain.model.PositionedLayoutComponent
import me.magnum.melonds.utils.enumValueOfIgnoreCase

data class PositionedLayoutComponentDto(
    @SerializedName("rect")
    val rect: RectDto,
    @SerializedName("component")
    val component: String,
) {

    companion object {
        fun fromModel(positionedLayoutComponent: PositionedLayoutComponent): PositionedLayoutComponentDto {
            return PositionedLayoutComponentDto(
                RectDto.fromModel(positionedLayoutComponent.rect),
                positionedLayoutComponent.component.name,
            )
        }
    }

    fun toModel(): PositionedLayoutComponent {
        return PositionedLayoutComponent(rect.toModel(), enumValueOfIgnoreCase(component))
    }
}