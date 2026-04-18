package me.magnum.melonds.domain.model.retroachievements

import me.magnum.rcheevosapi.model.RAAchievementSet.Type
import me.magnum.rcheevosapi.model.RAGameId
import me.magnum.rcheevosapi.model.RALeaderboard
import me.magnum.rcheevosapi.model.RASetId
import java.net.URL

data class RAUserAchievementSet(
    val id: RASetId,
    val title: String?,
    val type: Type,
    val gameId: RAGameId,
    val iconUrl: URL,
    val achievements: List<RAUserAchievement>,
    val leaderboards: List<RALeaderboard>,
)