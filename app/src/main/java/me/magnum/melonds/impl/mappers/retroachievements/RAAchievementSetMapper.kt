package me.magnum.melonds.impl.mappers.retroachievements

import me.magnum.melonds.database.entities.retroachievements.RAAchievementSetEntity
import me.magnum.rcheevosapi.model.RAAchievement
import me.magnum.rcheevosapi.model.RAAchievementSet
import me.magnum.rcheevosapi.model.RAGameId
import me.magnum.rcheevosapi.model.RALeaderboard
import me.magnum.rcheevosapi.model.RASetId
import java.net.URI

fun RAAchievementSet.mapToEntity(): RAAchievementSetEntity {
    return RAAchievementSetEntity(
        id = id.id,
        gameId = gameId.id,
        title = title,
        type = type.name,
        iconUrl = iconUrl.toString(),
    )
}

fun RAAchievementSetEntity.mapToModel(achievements: List<RAAchievement>, leaderboards: List<RALeaderboard>): RAAchievementSet {
    return RAAchievementSet(
        id = RASetId(id),
        gameId = RAGameId(gameId),
        title = title,
        type = RAAchievementSet.Type.valueOf(type),
        iconUrl = URI(iconUrl).toURL(),
        achievements = achievements,
        leaderboards = leaderboards,
    )
}