package me.magnum.rcheevosapi.dto.mapper

import me.magnum.rcheevosapi.dto.AchievementSetDto
import me.magnum.rcheevosapi.dto.GameAchievementSetsDto
import me.magnum.rcheevosapi.model.RAAchievementSet
import me.magnum.rcheevosapi.model.RAGame
import me.magnum.rcheevosapi.model.RAGameId
import me.magnum.rcheevosapi.model.RASetId
import java.net.URI

private const val MAX_SETS = 64
private const val MAX_ACHIEVEMENTS = 10_000
private const val MAX_LEADERBOARDS = 5_000
private const val MAX_TEXT_CHARS = 16 * 1024
private const val MAX_EXPRESSION_CHARS = 64 * 1024
private const val MAX_URL_CHARS = 8 * 1024

internal fun GameAchievementSetsDto.mapToModel(): RAGame {
    validateBudget()
    return RAGame(
        id = RAGameId(id),
        title = title,
        icon = URI(iconUrl).toURL(),
        richPresencePatch = richPresencePatch,
        sets = sets?.map { it.mapToModel() }.orEmpty(),
    )
}

private fun GameAchievementSetsDto.validateBudget() {
    require(title.length <= MAX_TEXT_CHARS && iconUrl.length <= MAX_URL_CHARS)
    require((richPresencePatch?.length ?: 0) <= MAX_EXPRESSION_CHARS)
    val achievementSets = sets.orEmpty()
    require(achievementSets.size <= MAX_SETS)

    var achievementCount = 0L
    var leaderboardCount = 0L
    achievementSets.forEach { set ->
        achievementCount += set.achievements.size
        leaderboardCount += set.leaderboards.size
        require(achievementCount <= MAX_ACHIEVEMENTS && leaderboardCount <= MAX_LEADERBOARDS)
        require((set.title?.length ?: 0) <= MAX_TEXT_CHARS && set.iconUrl.length <= MAX_URL_CHARS)
        set.achievements.forEach { achievement ->
            require(achievement.title.length <= MAX_TEXT_CHARS)
            require(achievement.description.length <= MAX_TEXT_CHARS)
            require(achievement.badgeUrl.length <= MAX_URL_CHARS)
            require(achievement.badgeUrlLocked.length <= MAX_URL_CHARS)
            require(achievement.memoryAddress.length <= MAX_EXPRESSION_CHARS)
        }
        set.leaderboards.forEach { leaderboard ->
            require(leaderboard.title.length <= MAX_TEXT_CHARS)
            require(leaderboard.description.length <= MAX_TEXT_CHARS)
            require(leaderboard.mem.length <= MAX_EXPRESSION_CHARS)
            require(leaderboard.format.length <= MAX_EXPRESSION_CHARS)
        }
    }
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
        "challenge" -> RAAchievementSet.Type.Challenge
        else -> RAAchievementSet.Type.Bonus
    }
}
