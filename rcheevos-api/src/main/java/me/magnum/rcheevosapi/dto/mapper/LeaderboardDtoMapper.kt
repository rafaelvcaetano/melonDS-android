package me.magnum.rcheevosapi.dto.mapper

import me.magnum.rcheevosapi.dto.LeaderboardDto
import me.magnum.rcheevosapi.model.RAGameId
import me.magnum.rcheevosapi.model.RALeaderboard
import me.magnum.rcheevosapi.model.RASetId

internal fun LeaderboardDto.mapToModel(gameId: RAGameId, setId: RASetId): RALeaderboard {
    return RALeaderboard(
        id = id,
        gameId = gameId,
        setId = setId,
        mem = mem,
        format = format,
        lowerIsBetter = lowerIsBetter,
        title = title,
        description = description,
        hidden = hidden,
    )
}