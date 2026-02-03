package me.magnum.melonds.domain.model.retroachievements

import me.magnum.rcheevosapi.model.RAAchievementSet.Type
import me.magnum.rcheevosapi.model.RAGameId
import java.net.URL

data class RAAchievementSetSummary(
    val setId: Long,
    val gameId: RAGameId,
    val title: String?,
    val type: Type,
    val iconUrl: URL,
)