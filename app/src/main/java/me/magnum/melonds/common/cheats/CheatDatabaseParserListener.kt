package me.magnum.melonds.common.cheats

import me.magnum.melonds.domain.model.CheatDatabase
import me.magnum.melonds.domain.model.Game

interface CheatDatabaseParserListener {
    fun onDatabaseParseStart(databaseName: String): CheatDatabase
    fun onGameParseStart(gameName: String)
    fun onGameParsed(game: Game)
    fun onParseComplete()
}