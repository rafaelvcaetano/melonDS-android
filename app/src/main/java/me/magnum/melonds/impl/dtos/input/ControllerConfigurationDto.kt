package me.magnum.melonds.impl.dtos.input

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import me.magnum.melonds.domain.model.ControllerConfiguration

@Serializable
data class ControllerConfigurationDto(
    @SerialName("inputMapper") val inputMapper: List<InputConfigDto>,
) {

    companion object {
        fun fromControllerConfiguration(controllerConfiguration: ControllerConfiguration): ControllerConfigurationDto {
            val inputMapping = controllerConfiguration.inputMapper.map { InputConfigDto.fromInputConfig(it) }
            return ControllerConfigurationDto(inputMapping)
        }
    }

    fun toControllerConfiguration(): ControllerConfiguration {
        return ControllerConfiguration(inputMapper.map { it.toInputConfig() })
    }
}