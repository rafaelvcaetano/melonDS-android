package me.magnum.melonds.common.cheats

interface CheatDatabaseParser {
    fun parseCheatDatabase(databaseStream: ProgressTrackerInputStream, parseListener: CheatDatabaseParserListener)
}