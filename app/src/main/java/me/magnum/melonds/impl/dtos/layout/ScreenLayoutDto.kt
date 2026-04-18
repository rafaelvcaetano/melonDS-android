package me.magnum.melonds.impl.dtos.layout

import com.google.gson.annotations.SerializedName
import me.magnum.melonds.domain.model.layout.ScreenLayout
import me.magnum.melonds.utils.enumValueOfIgnoreCase
import java.util.UUID

data class ScreenLayoutDto(
    @SerializedName("backgroundId")
    val backgroundId: String?,
    @SerializedName("backgroundMode")
    val backgroundMode: String,
    @SerializedName("components")
    val components: List<PositionedLayoutComponentDto>?,
) {

    companion object {
        fun fromModel(screenLayout: ScreenLayout): ScreenLayoutDto {
            return ScreenLayoutDto(
                backgroundId = screenLayout.backgroundId?.toString(),
                backgroundMode = screenLayout.backgroundMode.name,
                components = screenLayout.components?.map {
                    PositionedLayoutComponentDto.fromModel(it)
                },
            )
        }
    }

    fun toModel(): ScreenLayout {
        return ScreenLayout(
            backgroundId = backgroundId?.let { UUID.fromString(it) },
            backgroundMode = enumValueOfIgnoreCase(backgroundMode),
            components = components?.map {
                it.toModel()
            },
        )
    }
}