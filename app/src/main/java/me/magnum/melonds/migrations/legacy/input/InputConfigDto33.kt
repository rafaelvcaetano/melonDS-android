package me.magnum.melonds.migrations.legacy.input

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import me.magnum.melonds.domain.model.Input

@Serializable
data class InputConfigDto33(
    @SerialName("a") val input: Input,
    @SerialName("b") val key: Int,
)