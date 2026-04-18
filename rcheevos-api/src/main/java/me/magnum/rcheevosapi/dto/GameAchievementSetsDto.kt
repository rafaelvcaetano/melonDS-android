package me.magnum.rcheevosapi.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class GameAchievementSetsDto(
    @SerialName("GameId")
    val id: Long,
    @SerialName("Title")
    val title: String,
    @SerialName("ImageIconUrl")
    val iconUrl: String,
    @SerialName("RichPresenceGameId")
    val richPresenceGameId: Long?,
    @SerialName("RichPresencePatch")
    val richPresencePatch: String?,
    @SerialName("Sets")
    val sets: List<AchievementSetDto>?,
)