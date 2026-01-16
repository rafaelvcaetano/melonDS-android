package me.magnum.rcheevosapi.dto.mapper

import me.magnum.rcheevosapi.dto.LeaderboardDto
import me.magnum.rcheevosapi.model.RAGameId
import me.magnum.rcheevosapi.model.RALeaderboard

internal fun LeaderboardDto.mapToModel(gameId: RAGameId): RALeaderboard {
    return RALeaderboard(
        id = id,
        gameId = gameId,
        mem = mem,
        format = format,
        lowerIsBetter = lowerIsBetter,
        title = title,
        description = description,
        hidden = hidden,
    )
}