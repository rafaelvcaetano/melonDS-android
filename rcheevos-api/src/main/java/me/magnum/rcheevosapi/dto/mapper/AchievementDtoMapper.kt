package me.magnum.rcheevosapi.dto.mapper

import me.magnum.rcheevosapi.dto.AchievementDto
import me.magnum.rcheevosapi.model.RAAchievement
import me.magnum.rcheevosapi.model.RAGameId
import java.net.URI

internal fun AchievementDto.mapToModel(gameId: RAGameId): RAAchievement {
    return RAAchievement(
        id = id.toLong(),
        gameId = gameId,
        totalAwardsCasual = numAwarded,
        totalAwardsHardcore = numAwardedHardcore,
        title = title,
        description = description,
        points = points.toIntOrNull() ?: 0,
        displayOrder = displayOrder?.toIntOrNull() ?: 0,
        badgeUrlUnlocked = URI(badgeUrl).toURL(),
        badgeUrlLocked = URI(badgeUrlLocked).toURL(),
        memoryAddress = memoryAddress,
        type = achievementFlagsToType(flags),
    )
}

private fun achievementFlagsToType(achievementFlags: Int): RAAchievement.Type {
    return when (achievementFlags) {
        3 -> RAAchievement.Type.CORE
        else -> RAAchievement.Type.UNOFFICIAL
    }
}