package me.magnum.rcheevosapi.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class GamePatchDto(
    @SerialName("PatchData")
    val game: GameDto,
)
