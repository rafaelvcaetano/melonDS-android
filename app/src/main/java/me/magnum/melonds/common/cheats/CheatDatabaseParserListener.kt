package me.magnum.melonds.common.cheats

import me.magnum.melonds.domain.model.Game

interface CheatDatabaseParserListener {
    fun onGameParseStart(gameName: String)
    fun onGameParsed(game: Game)
    fun onParseComplete()
}