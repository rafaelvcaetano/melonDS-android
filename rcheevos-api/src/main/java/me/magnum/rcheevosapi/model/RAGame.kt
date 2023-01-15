package me.magnum.rcheevosapi.model

import java.net.URL

data class RAGame(
    val id: RAGameId,
    val title: String,
    val icon: URL,
    val totalAchievements: Int,
    val numPlayersCasual: Int,
    val numPlayersHardcore: Int,
    val achievements: List<RAAchievement>,
)