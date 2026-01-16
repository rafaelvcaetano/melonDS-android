package me.magnum.rcheevosapi.model

data class RALeaderboard(
    val id: Long,
    val gameId: RAGameId,
    val mem: String,
    val format: String,
    val lowerIsBetter: Boolean,
    val title: String,
    val description: String,
    val hidden: Boolean,
)