package me.magnum.rcheevosapi.dto.mapper

import me.magnum.rcheevosapi.dto.AchievementDto
import me.magnum.rcheevosapi.model.RAAchievement
import java.net.URL

internal fun AchievementDto.mapToModel(): RAAchievement {
    return RAAchievement(
        id = id.toLong(),
        totalAwardsCasual = numAwarded,
        totalAwardsHardcore = numAwardedHardcore,
        title = title,
        description = description,
        points = points.toIntOrNull() ?: 0,
        displayOrder = displayOrder?.toIntOrNull() ?: 0,
        badgeUrlUnlocked = URL(badgeUrl),
        badgeUrlLocked = URL(badgeUrlLocked),
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