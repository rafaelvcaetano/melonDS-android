package me.magnum.melonds.impl.mappers.retroachievements

import me.magnum.melonds.database.entities.retroachievements.RAAchievementEntity
import me.magnum.rcheevosapi.model.RAAchievement
import me.magnum.rcheevosapi.model.RAGameId
import me.magnum.rcheevosapi.model.RASetId
import java.net.URL

fun RAAchievement.mapToEntity(): RAAchievementEntity {
    return RAAchievementEntity(
        id,
        gameId.id,
        setId.id,
        totalAwardsCasual ?: 0,
        totalAwardsHardcore ?: 0,
        title,
        description,
        points,
        displayOrder,
        badgeUrlUnlocked.toString(),
        badgeUrlLocked.toString(),
        memoryAddress,
        type.toEntityType(),
    )
}

fun RAAchievementEntity.mapToModel(): RAAchievement {
    return RAAchievement(
        id,
        RAGameId(gameId),
        RASetId(setId),
        totalAwardsCasual,
        totalAwardsHardcore,
        title,
        description,
        points,
        displayOrder,
        URL(badgeUrlUnlocked),
        URL(badgeUrlLocked),
        memoryAddress,
        parseAchievementType(type),
    )
}

private fun RAAchievement.Type.toEntityType(): Int {
    return when (this) {
        RAAchievement.Type.CORE -> 0
        RAAchievement.Type.UNOFFICIAL -> 1
    }
}

private fun parseAchievementType(type: Int): RAAchievement.Type {
    return when (type) {
        0 -> RAAchievement.Type.CORE
        1 -> RAAchievement.Type.UNOFFICIAL
        else -> throw UnsupportedOperationException("Unknown achievement type: $type")
    }
}