package me.magnum.rcheevosapi.dto

import com.google.gson.annotations.SerializedName

internal data class AchievementDto(
    @SerializedName("ID")
    val id: String,
    @SerializedName("NumAwarded")
    val numAwarded: Int,
    @SerializedName("NumAwardedHardcore")
    val numAwardedHardcore: Int,
    @SerializedName("Title")
    val title: String,
    @SerializedName("Description")
    val description: String,
    @SerializedName("Points")
    val points: String,
    @SerializedName("Flags")
    val flags: Int,
    @SerializedName("BadgeURL")
    val badgeUrl: String,
    @SerializedName("BadgeLockedURL")
    val badgeUrlLocked: String,
    @SerializedName("DisplayOrder")
    val displayOrder: String?,
    @SerializedName("MemAddr")
    val memoryAddress: String,
)
