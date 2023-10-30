package me.magnum.melonds.impl.dtos.layout

import com.google.gson.annotations.SerializedName
import me.magnum.melonds.domain.model.UILayout
import me.magnum.melonds.utils.enumValueOfIgnoreCase
import java.util.UUID

data class UILayoutDto(
    @SerializedName("backgroundId")
    val backgroundId: String?,
    @SerializedName("backgroundMode")
    val backgroundMode: String,
    @SerializedName("components")
    val components: List<PositionedLayoutComponentDto>,
) {

    companion object {
        fun fromModel(uiLayout: UILayout): UILayoutDto {
            return UILayoutDto(
                uiLayout.backgroundId?.toString(),
                uiLayout.backgroundMode.name,
                uiLayout.components.map {
                    PositionedLayoutComponentDto.fromModel(it)
                },
            )
        }
    }

    fun toModel(): UILayout {
        return UILayout(
            backgroundId?.let { UUID.fromString(it) },
            enumValueOfIgnoreCase(backgroundMode),
            components.map {
                it.toModel()
            },
        )
    }
}