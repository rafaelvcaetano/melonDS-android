package me.magnum.melonds.common.cheats

import me.magnum.melonds.domain.model.CheatDatabase
import me.magnum.melonds.domain.model.Game
import me.magnum.melonds.impl.XmlCheatDatabaseSAXHandler
import javax.xml.parsers.SAXParserFactory
import javax.xml.XMLConstants
import org.xml.sax.InputSource

class XmlCheatDatabaseParser : CheatDatabaseParser {
    override fun parseCheatDatabase(databaseStream: ProgressTrackerInputStream, parseListener: CheatDatabaseParserListener) {
        val saxFactory = SAXParserFactory.newInstance()
        val parser = saxFactory.newSAXParser()
        val reader = parser.xmlReader
        // Android's Expat reader does not recognize the Xerces-only feature names.
        // Apply every provider feature opportunistically; the streaming token guard
        // and rejecting EntityResolver below are the provider-independent boundary.
        listOf(
            XMLConstants.FEATURE_SECURE_PROCESSING to true,
            "http://apache.org/xml/features/disallow-doctype-decl" to true,
            "http://xml.org/sax/features/external-general-entities" to false,
            "http://xml.org/sax/features/external-parameter-entities" to false,
            "http://apache.org/xml/features/nonvalidating/load-external-dtd" to false,
        ).forEach { (feature, enabled) -> runCatching { reader.setFeature(feature, enabled) } }
        val handler = XmlCheatDatabaseSAXHandler(object : XmlCheatDatabaseSAXHandler.HandlerListener {
            override fun onCheatDatabaseParseStart(databaseName: String): CheatDatabase {
                return parseListener.onDatabaseParseStart(databaseName)
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
        reader.contentHandler = handler
        reader.entityResolver = handler
        // Expat and the host SAX provider both expose the lexical callback.
        // Requiring it lets us reject actual DTD declarations without treating
        // harmless text inside comments or CDATA as markup.
        reader.setProperty("http://xml.org/sax/properties/lexical-handler", handler)
        reader.parse(InputSource(databaseStream))
    }
}
