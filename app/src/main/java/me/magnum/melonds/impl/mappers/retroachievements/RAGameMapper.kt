package me.magnum.melonds.impl.mappers.retroachievements

import me.magnum.melonds.database.entities.retroachievements.RAGameEntity
import me.magnum.rcheevosapi.model.RAAchievement
import me.magnum.rcheevosapi.model.RAGame
import me.magnum.rcheevosapi.model.RAGameId
import me.magnum.rcheevosapi.model.RALeaderboard
import java.net.URL

fun RAGame.mapToEntity(): RAGameEntity {
    return RAGameEntity(
        gameId = id.id,
        richPresencePatch = richPresencePatch,
        title = title,
        icon = icon.toString(),
    )
}

fun RAGameEntity.mapToModel(achievements: List<RAAchievement>, leaderboards: List<RALeaderboard>): RAGame {
    return RAGame(
        id = RAGameId(gameId),
        richPresencePatch = richPresencePatch,
        title = title,
        icon = URL(icon),
        achievements = achievements,
        leaderboards = leaderboards,
    )
}