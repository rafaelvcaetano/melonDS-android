package me.magnum.melonds.migrations.legacy.input

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ControllerConfigurationDto33(
    @SerialName("a") val inputMapper: List<InputConfigDto33>,
)