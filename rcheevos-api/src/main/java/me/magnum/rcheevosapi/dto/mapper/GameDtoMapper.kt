package me.magnum.rcheevosapi.dto.mapper

import me.magnum.rcheevosapi.dto.GameDto
import me.magnum.rcheevosapi.model.RAGame
import me.magnum.rcheevosapi.model.RAGameId
import java.net.URL

internal fun GameDto.mapToModel(): RAGame {
    return RAGame(
        id = RAGameId(id.toLong()),
        title = title,
        icon = URL(iconUrl),
        totalAchievements = totalAchievements,
        numPlayersCasual = numDistinctPlayersCasual,
        numPlayersHardcore = numDistinctPlayersHardcore,
        achievements = achievements.map {
            it.mapToModel()
        },
    )
}