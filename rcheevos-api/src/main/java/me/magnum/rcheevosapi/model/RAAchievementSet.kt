package me.magnum.rcheevosapi.model

import java.net.URL

data class RAAchievementSet(
    val id: RASetId,
    val gameId: RAGameId,
    val title: String?,
    val type: Type,
    val iconUrl: URL,
    val achievements: List<RAAchievement>,
    val leaderboards: List<RALeaderboard>,
) {

    enum class Type {
        Core,
        Bonus,
        Specialty,
        Exclusive,
    }
}