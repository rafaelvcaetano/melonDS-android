package me.magnum.rcheevosapi.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class AchievementDto(
    @SerialName("ID")
    val id: Long,
    @SerialName("NumAwarded")
    val numAwarded: Int?,
    @SerialName("NumAwardedHardcore")
    val numAwardedHardcore: Int?,
    @SerialName("Title")
    val title: String,
    @SerialName("Description")
    val description: String,
    @SerialName("Points")
    val points: Int,
    @SerialName("Flags")
    val flags: Int,
    @SerialName("BadgeURL")
    val badgeUrl: String,
    @SerialName("BadgeLockedURL")
    val badgeUrlLocked: String,
    @SerialName("DisplayOrder")
    val displayOrder: String?,
    @SerialName("MemAddr")
    val memoryAddress: String,
)
