package me.magnum.rcheevosapi.dto

import com.google.gson.annotations.SerializedName

internal data class GamePatchDto(
    @SerializedName("PatchData")
    val game: GameDto,
)
