package me.magnum.melonds.impl.dtos.input

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import me.magnum.melonds.domain.model.Input
import me.magnum.melonds.domain.model.InputConfig

@Serializable
data class InputConfigDto(
    @SerialName("input") val input: Input,
    @SerialName("assignment") val assignment: AssignmentDto,
) {

    @Serializable
    sealed class AssignmentDto {
        @SerialName("deviceId") abstract val deviceId: Int?

        @Serializable
        @SerialName("none")
        data object None : AssignmentDto() {
            override val deviceId: Int? = null
        }

        @Serializable
        @SerialName("key")
        class Key(
            override val deviceId: Int?,
            @SerialName("keyCode") val keyCode: Int,
        ) : AssignmentDto()

        @Serializable
        @SerialName("axis")
        class Axis(
            override val deviceId: Int?,
            @SerialName("axisCode") val axisCode: Int,
            @SerialName("direction") val direction: InputConfig.Assignment.Axis.Direction,
        ) : AssignmentDto()
    }

    companion object {
        fun fromInputConfig(inputConfig: InputConfig): InputConfigDto {
            return InputConfigDto(
                input = inputConfig.input,
                assignment = when (inputConfig.assignment) {
                    is InputConfig.Assignment.None -> AssignmentDto.None
                    is InputConfig.Assignment.Key -> AssignmentDto.Key(inputConfig.assignment.deviceId, inputConfig.assignment.keyCode)
                    is InputConfig.Assignment.Axis -> AssignmentDto.Axis(inputConfig.assignment.deviceId, inputConfig.assignment.axisCode, inputConfig.assignment.direction)
                }
            )
        }
    }

    fun toInputConfig(): InputConfig {
        return InputConfig(
            input = input,
            assignment = when (assignment) {
                is AssignmentDto.None -> InputConfig.Assignment.None
                is AssignmentDto.Key -> InputConfig.Assignment.Key(assignment.deviceId, assignment.keyCode)
                is AssignmentDto.Axis -> InputConfig.Assignment.Axis(assignment.deviceId, assignment.axisCode, assignment.direction)
            }
        )
    }
}