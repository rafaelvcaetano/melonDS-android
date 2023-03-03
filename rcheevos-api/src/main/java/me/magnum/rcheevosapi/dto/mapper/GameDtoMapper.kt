package me.magnum.rcheevosapi.dto.mapper

import me.magnum.rcheevosapi.dto.GameDto
import me.magnum.rcheevosapi.model.RAGame
import me.magnum.rcheevosapi.model.RAGameId

internal fun GameDto.mapToModel(): RAGame {
    val gameId = RAGameId(id.toLong())
    return RAGame(
        id = gameId,
        title = title,
        richPresencePatch = richPresencePatch,
        achievements = achievements.map {
            it.mapToModel(gameId)
        },
    )
}