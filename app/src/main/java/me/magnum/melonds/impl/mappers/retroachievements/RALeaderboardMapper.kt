package me.magnum.melonds.impl.mappers.retroachievements

import me.magnum.melonds.database.entities.retroachievements.RALeaderboardEntity
import me.magnum.rcheevosapi.model.RAGameId
import me.magnum.rcheevosapi.model.RALeaderboard

fun RALeaderboard.mapToEntity(): RALeaderboardEntity {
    return RALeaderboardEntity(
        id = id,
        gameId = gameId.id,
        mem = mem,
        format = format,
        lowerIsBetter = lowerIsBetter,
        title = title,
        description = description,
        hidden = hidden,
    )
}

fun RALeaderboardEntity.mapToModel(): RALeaderboard {
    return RALeaderboard(
        id = id,
        gameId = RAGameId(gameId),
        mem = mem,
        format = format,
        lowerIsBetter = lowerIsBetter,
        title = title,
        description = description,
        hidden = hidden,
    )
}