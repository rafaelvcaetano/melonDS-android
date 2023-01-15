package me.magnum.rcheevosapi.dto

import com.google.gson.annotations.SerializedName

internal data class GameIdDto(
    @SerializedName("GameId")
    val gameId: Long,
)
