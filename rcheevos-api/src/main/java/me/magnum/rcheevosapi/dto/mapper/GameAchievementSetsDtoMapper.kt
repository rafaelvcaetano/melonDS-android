package me.magnum.rcheevosapi.dto.mapper

import me.magnum.rcheevosapi.dto.AchievementSetDto
import me.magnum.rcheevosapi.dto.GameAchievementSetsDto
import me.magnum.rcheevosapi.model.RAAchievementSet
import me.magnum.rcheevosapi.model.RAGame
import me.magnum.rcheevosapi.model.RAGameId
import me.magnum.rcheevosapi.model.RASetId
import java.net.URI

internal fun GameAchievementSetsDto.mapToModel(): RAGame {
    return RAGame(
        id = RAGameId(id),
        title = title,
        icon = URI(iconUrl).toURL(),
        richPresencePatch = richPresencePatch,
        sets = sets?.map { it.mapToModel() }.orEmpty(),
    )
}

private fun AchievementSetDto.mapToModel(): RAAchievementSet {
    val gameId = RAGameId(gameId)
    val setId = RASetId(setId)
    return RAAchievementSet(
        id = setId,
        gameId = gameId,
        title = title,
        type = parseAchievementSetType(type),
        iconUrl = URI(iconUrl).toURL(),
        achievements = achievements.map { it.mapToModel(gameId, setId) },
        leaderboards = leaderboards.map { it.mapToModel(gameId, setId) },
    )
}

private fun parseAchievementSetType(type: String): RAAchievementSet.Type {
    return when (type) {
        "core" -> RAAchievementSet.Type.Core
        "bonus" -> RAAchievementSet.Type.Bonus
        "specialty" -> RAAchievementSet.Type.Specialty
        "exclusive" -> RAAchievementSet.Type.Exclusive
        else -> throw IllegalArgumentException("Unknown achievement set type: $type")
    }
}