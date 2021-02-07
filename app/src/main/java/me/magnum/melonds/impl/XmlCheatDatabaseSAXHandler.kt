package me.magnum.melonds.impl

import me.magnum.melonds.domain.model.Cheat
import me.magnum.melonds.domain.model.CheatFolder
import me.magnum.melonds.domain.model.Game
import org.xml.sax.Attributes
import org.xml.sax.helpers.DefaultHandler

class XmlCheatDatabaseSAXHandler(private val listener: HandlerListener) : DefaultHandler() {
    interface HandlerListener {
        fun onGameParseStart(gameName: String)
        fun onGameParsed(game: Game)
        fun onParseComplete()
    }

    private var parsingGame = false
    private var parsingGameName = false
    private var parsingGameCode = false
    private var parsingFolder = false
    private var parsingFolderName = false
    private var parsingCheat = false
    private var parsingCheatName = false
    private var parsingCheatDescription = false
    private var parsingCheatCodes = false

    private var gameName: String? = null
    private var gameCode: String? = null
    private var folderName: String? = null
    private var cheatName: String? = null
    private var cheatDescription: String? = null
    private var cheatCodes: String? = null

    private val currentFolderCheats = mutableListOf<Cheat>()
    private val currentGameFolders = mutableListOf<CheatFolder>()

    override fun startElement(uri: String?, localName: String?, qName: String?, attributes: Attributes?) {
        if (!parsingGame) {
            if (qName == "game") {
                parsingGame = true
            }
            return
        }

        if (parsingGame && gameName == null && qName == "name") {
            parsingGameName = true
        }

        if (parsingGame && !parsingGameCode && qName == "gameid") {
            parsingGameCode = true
        }

        if (!parsingFolder && qName == "folder") {
            parsingFolder = true
        }

        if (parsingFolder && folderName == null && qName == "name") {
            parsingFolderName = true
        }

        if (parsingFolder && !parsingCheat && qName == "cheat") {
            parsingCheat = true
        }

        if (parsingCheat && cheatName == null && qName == "name") {
            parsingCheatName = true
        }

        if (parsingCheat && cheatDescription == null && qName == "note") {
            parsingCheatDescription = true
        }

        if (parsingCheat && cheatCodes == null && qName == "codes") {
            parsingCheatCodes = true
        }
    }

    override fun endElement(uri: String?, localName: String?, qName: String?) {
        if (parsingCheat && qName == "cheat") {
            parsingCheat = false
            parsingCheatName = false
            parsingCheatDescription = false
            parsingCheatCodes = false

            currentFolderCheats.add(Cheat(null, cheatName!!, cheatDescription, cheatCodes!!, false))
            cheatName = null
            cheatDescription = null
            cheatCodes = null
            return
        }

        if (parsingFolder && qName == "folder") {
            parsingFolder = false
            parsingFolderName = false

            if (currentFolderCheats.isNotEmpty()) {
                currentGameFolders.add(CheatFolder(null, folderName!!, ArrayList(currentFolderCheats)))
            }
            folderName = null
            currentFolderCheats.clear()
            return
        }

        if (parsingGame && qName == "game") {
            parsingGame = false
            parsingGameName = false

            if (currentGameFolders.isNotEmpty()) {
                emitGame(Game(null, gameName!!, gameCode!!, ArrayList(currentGameFolders)))
            }
            currentGameFolders.clear()
            gameName = null
        }
    }

    override fun characters(ch: CharArray?, start: Int, length: Int) {
        if (!parsingGame || ch == null) {
            return
        }

        if (parsingGameName) {
            gameName = String(ch, 0, length)
            parsingGameName = false
            emitGameNameParsed(gameName!!)
            return
        }

        if (parsingGameCode) {
            gameCode = String(ch, 0, 4)
            parsingGameCode = false
            return
        }

        if (parsingFolder && parsingFolderName) {
            folderName = String(ch, 0, length)
            parsingFolderName = false
            return
        }

        if (parsingCheat && parsingCheatName) {
            cheatName = String(ch, 0, length)
            parsingCheatName = false
            return
        }

        if (parsingCheat && parsingCheatDescription) {
            val descriptionString = String(ch, 0, length)
            if (descriptionString.isNotBlank()) {
                cheatDescription = descriptionString
            }
            parsingCheatDescription = false
            return
        }

        if (parsingCheat && parsingCheatCodes) {
            cheatCodes = String(ch, 0, length)
            parsingCheatCodes = false
            return
        }
    }

    override fun endDocument() {
        listener.onParseComplete()
    }

    private fun emitGameNameParsed(gameName: String) {
        listener.onGameParseStart(gameName)
    }

    private fun emitGame(game: Game) {
        listener.onGameParsed(game)
    }
}