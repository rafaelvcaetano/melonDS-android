package me.magnum.melonds.impl.dtos.layout

import com.google.gson.annotations.SerializedName
import me.magnum.melonds.domain.model.LayoutConfiguration
import me.magnum.melonds.utils.enumValueOfIgnoreCase
import java.util.UUID

data class LayoutConfigurationDto(
    @SerializedName("id")
    val id: String?,
    @SerializedName("name")
    val name: String?,
    @SerializedName("type")
    val type: String,
    @SerializedName("orientation")
    val orientation: String,
    @SerializedName("useCustomOpacity")
    val useCustomOpacity: Boolean,
    @SerializedName("opacity")
    val opacity: Int,
    @SerializedName("portraitLayout")
    val portraitLayout: UILayoutDto,
    @SerializedName("landscapeLayout")
    val landscapeLayout: UILayoutDto
) {

    companion object {
        fun fromModel(layoutConfiguration: LayoutConfiguration): LayoutConfigurationDto {
            return LayoutConfigurationDto(
                layoutConfiguration.id?.toString(),
                layoutConfiguration.name,
                layoutConfiguration.type.name,
                layoutConfiguration.orientation.name,
                layoutConfiguration.useCustomOpacity,
                layoutConfiguration.opacity,
                UILayoutDto.fromModel(layoutConfiguration.portraitLayout),
                UILayoutDto.fromModel(layoutConfiguration.landscapeLayout),
            )
        }
    }

    fun toModel(): LayoutConfiguration {
        return LayoutConfiguration(
            id?.let { UUID.fromString(it) },
            name,
            enumValueOfIgnoreCase(type),
            enumValueOfIgnoreCase(orientation),
            useCustomOpacity,
            opacity,
            portraitLayout.toModel(),
            landscapeLayout.toModel(),
        )
    }
}