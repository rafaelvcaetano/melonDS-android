package me.magnum.rcheevosapi.dto.mapper

import me.magnum.rcheevosapi.dto.GameDto
import me.magnum.rcheevosapi.model.RAGame
import me.magnum.rcheevosapi.model.RAGameId
import java.net.URL

internal fun GameDto.mapToModel(): RAGame {
    val gameId = RAGameId(id.toLong())
    return RAGame(
        id = gameId,
        title = title,
        icon = URL(iconUrl),
        richPresencePatch = richPresencePatch,
        achievements = achievements.map {
            it.mapToModel(gameId)
        },
    )
}