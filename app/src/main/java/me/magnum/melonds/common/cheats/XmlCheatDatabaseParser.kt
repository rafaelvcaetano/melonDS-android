package me.magnum.melonds.common.cheats

import me.magnum.melonds.domain.model.Game
import me.magnum.melonds.impl.XmlCheatDatabaseSAXHandler
import javax.xml.parsers.SAXParserFactory

class XmlCheatDatabaseParser : CheatDatabaseParser {
    override fun parseCheatDatabase(databaseStream: ProgressTrackerInputStream, parseListener: CheatDatabaseParserListener) {
        val saxFactory = SAXParserFactory.newInstance()
        val parser = saxFactory.newSAXParser()
        val handler = XmlCheatDatabaseSAXHandler(object : XmlCheatDatabaseSAXHandler.HandlerListener {
            override fun onCheatDatabaseParseStart(databaseName: String) {
                parseListener.onDatabaseParseStart(databaseName)
            }

            override fun onGameParseStart(gameName: String) {
                parseListener.onGameParseStart(gameName)
            }

            override fun onGameParsed(game: Game) {
                parseListener.onGameParsed(game)
            }

            override fun onParseComplete() {
                parseListener.onParseComplete()
            }

        })
        parser.parse(databaseStream, handler)
    }
}