package me.magnum.melonds.impl.mappers.retroachievements

import me.magnum.melonds.database.entities.retroachievements.RAAchievementEntity
import me.magnum.rcheevosapi.model.RAAchievement
import me.magnum.rcheevosapi.model.RAGameId
import java.net.URL

fun RAAchievement.mapToEntity(gameId: RAGameId): RAAchievementEntity {
    return RAAchievementEntity(
        id,
        gameId.id,
        totalAwardsCasual,
        totalAwardsHardcore,
        title,
        description,
        points,
        displayOrder,
        badgeUrlUnlocked.toString(),
        badgeUrlLocked.toString(),
        memoryAddress,
        type.ordinal,
    )
}

fun RAAchievementEntity.mapToModel(): RAAchievement {
    return RAAchievement(
        id,
        totalAwardsCasual,
        totalAwardsHardcore,
        title,
        description,
        points,
        displayOrder,
        URL(badgeUrlUnlocked),
        URL(badgeUrlLocked),
        memoryAddress,
        RAAchievement.Type.values()[type],
    )
}