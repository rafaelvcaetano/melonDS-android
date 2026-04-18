package me.magnum.rcheevosapi.dto.mapper

import me.magnum.rcheevosapi.dto.AchievementDto
import me.magnum.rcheevosapi.model.RAAchievement
import me.magnum.rcheevosapi.model.RAGameId
import me.magnum.rcheevosapi.model.RASetId
import java.net.URI

internal fun AchievementDto.mapToModel(gameId: RAGameId, setId: RASetId): RAAchievement {
    return RAAchievement(
        id = id,
        gameId = gameId,
        setId = setId,
        totalAwardsCasual = numAwarded,
        totalAwardsHardcore = numAwardedHardcore,
        title = title,
        description = description,
        points = points,
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