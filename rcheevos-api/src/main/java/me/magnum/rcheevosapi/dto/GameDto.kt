package me.magnum.rcheevosapi.dto

import com.google.gson.annotations.SerializedName

internal data class GameDto(
    @SerializedName("ID")
    val id: String,
    @SerializedName("Title")
    val title: String,
    @SerializedName("ImageIconURL")
    val iconUrl: String,
    @SerializedName("NumAchievements")
    val totalAchievements: Int,
    @SerializedName("NumDistinctPlayersCasual")
    val numDistinctPlayersCasual: Int,
    @SerializedName("NumDistinctPlayersHardcore")
    val numDistinctPlayersHardcore: Int,
    @SerializedName("Achievements")
    val achievements: List<AchievementDto>,
)
