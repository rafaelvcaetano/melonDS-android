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
    @SerialName("LBData")
    val leaderboardData: LeaderboardDataDto,
    @SerialName("Score")
    val score: Int,
    @SerialName("ScoreFormatted")
    val scoreFormatted: String,
    @SerialName("BestScore")
    val bestScore: Int,
    @SerialName("RankInfo")
    val rankInfo: RankInfoDto,
)

@Serializable
internal data class LeaderboardDataDto(
    @SerialName("Format")
    val format: String,
    @SerialName("LeaderboardID")
    val leaderboardId: Long,
    @SerialName("GameID")
    val gameId: Long,
    @SerialName("Title")
    val title: String,
    @SerialName("LowerIsBetter")
    val lowerIsBetter: Boolean,
)

@Serializable
internal data class RankInfoDto(
    @SerialName("NumEntries")
    val numEntries: Int,
    @SerialName("Rank")
    val rank: Int,
)