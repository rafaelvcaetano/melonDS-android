package me.magnum.rcheevosapi.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class RASubmitLeaderboardEntryResponseDto(
    @SerialName("Response")
    val response: ResponseDto,
)

@Serializable
internal data class ResponseDto(
    @SerialName("Score")
    val score: Int,
    @SerialName("BestScore")
    val bestScore: Int,
    @SerialName("RankInfo")
    val rankInfo: RankInfoDto,
)

@Serializable
internal data class RankInfoDto(
    @SerialName("NumEntries")
    val numEntries: Int,
    @SerialName("Rank")
    val rank: Int,
)