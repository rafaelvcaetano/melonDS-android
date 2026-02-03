package me.magnum.rcheevosapi.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class AchievementSetDto(
    @SerialName("Title")
    val title: String?,
    @SerialName("Type")
    val type: String,
    @SerialName("AchievementSetId")
    val setId: Long,
    @SerialName("GameId")
    val gameId: Long,
    @SerialName("ImageIconUrl")
    val iconUrl: String,
    @SerialName("Achievements")
    val achievements: List<AchievementDto>,
    @SerialName("Leaderboards")
    val leaderboards: List<LeaderboardDto>,
)