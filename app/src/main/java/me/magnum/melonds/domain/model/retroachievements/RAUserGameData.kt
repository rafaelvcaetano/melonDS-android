package me.magnum.melonds.domain.model.retroachievements

import me.magnum.rcheevosapi.model.RAGameId
import me.magnum.rcheevosapi.model.RALeaderboard
import java.net.URL

data class RAUserGameData(
    val id: RAGameId,
    val title: String,
    val icon: URL,
    val richPresencePatch: String?,
    val achievements: List<RAUserAchievement>,
    val leaderboards: List<RALeaderboard>,
)