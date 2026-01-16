package me.magnum.rcheevosapi.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class GameDto(
    @SerialName("ID")
    val id: Long,
    @SerialName("Title")
    val title: String,
    @SerialName("ImageIconURL")
    val iconUrl: String,
    @SerialName("RichPresencePatch")
    val richPresencePatch: String?,
    @SerialName("Achievements")
    val achievements: List<AchievementDto>,
    @SerialName("Leaderboards")
    val leaderboards: List<LeaderboardDto>,
)
