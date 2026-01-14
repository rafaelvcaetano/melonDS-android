package me.magnum.rcheevosapi.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class AwardAchievementResponseDto(
    @SerialName("Success")
    val success: Boolean,
    @SerialName("AchievementsRemaining")
    val achievementsRemaining: Int,
    @SerialName("Score")
    val score: Int,
    @SerialName("SoftcoreScore")
    val softcoreScore: Int,
    @SerialName("AchievementID")
    val achievementId: Int,
)