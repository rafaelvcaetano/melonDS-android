package me.magnum.melonds.impl

import me.magnum.melonds.domain.model.Cheat
import me.magnum.melonds.domain.model.CheatFolder
import me.magnum.melonds.domain.model.Game
import org.xml.sax.Attributes
import org.xml.sax.helpers.DefaultHandler

class XmlCheatDatabaseSAXHandler(private val listener: HandlerListener) : DefaultHandler() {
    interface HandlerListener {
        fun onCheatDatabaseParseStart(databaseName: String)
        fun onGameParseStart(gameName: String)
        fun onGameParsed(game: Game)
        fun onParseComplete()
    }

    private var parsingDatabase = false
    private var parsingDatabaseName = false
    private var parsingGame = false
    private var parsingGameName = false
    private var parsingGameCode = false
    private var parsingFolder = false
    private var parsingFolderName = false
    private var parsingCheat = false
    private var parsingCheatName = false
    private var parsingCheatDescription = false
    private var parsingCheatCodes = false
    private var parsingText = false

    private var databaseName: String? = null
    private var gameName: String? = null
    private var gameCode: String? = null
    private var gameChecksum: String? = null
    private var folderName: String? = null
    private var cheatName: String? = null
    private var cheatDescription: String? = null
    private var cheatCodes: String? = null
    private val textStringBuilder = StringBuilder()

    private val currentFolderCheats = mutableListOf<Cheat>()
    private val currentGameFolders = mutableListOf<CheatFolder>()

    override fun startElement(uri: String?, localName: String?, qName: String?, attributes: Attributes?) {
        if (!parsingDatabase) {
            if (qName == "codelist") {
                parsingDatabase = true
            }
            return
        }

        if (parsingDatabase && databaseName == null && qName == "name") {
            parsingDatabaseName = true
            parsingText = true
        }

        if (!parsingGame) {
            if (qName == "game") {
                parsingGame = true
            }
            return
        }

        if (parsingGame && gameName == null && qName == "name") {
            parsingGameName = true
            parsingText = true
        }

        if (parsingGame && !parsingGameCode && qName == "gameid") {
            parsingGameCode = true
            parsingText = true
        }

        if (!parsingFolder && qName == "folder") {
            parsingFolder = true
            parsingText = true
        }

        if (parsingFolder && folderName == null && qName == "name") {
            parsingFolderName = true
            parsingText = true
        }

        if (parsingFolder && !parsingCheat && qName == "cheat") {
            parsingCheat = true
            parsingText = true
        }

        if (parsingCheat && cheatName == null && qName == "name") {
            parsingCheatName = true
            parsingText = true
        }

        if (parsingCheat && cheatDescription == null && qName == "note") {
            parsingCheatDescription = true
            parsingText = true
        }

        if (parsingCheat && cheatCodes == null && qName == "codes") {
            parsingCheatCodes = true
            parsingText = true
        }

        if (parsingText) {
            textStringBuilder.clear()
        }
    }

    override fun endElement(uri: String?, localName: String?, qName: String?) {
        if (parsingDatabase) {
            if (parsingDatabaseName) {
                // Remove everything between parenthesis. Most likely it contains the DB's version
                databaseName = textStringBuilder.toString().replace("\\(.*?\\)".toRegex(), "")
                parsingDatabaseName = false
                emitCheatDatabaseName(databaseName!!)
            }
        }

        if (parsingGame) {
            if (parsingGameName) {
                gameName = textStringBuilder.toString()
                parsingGameName = false
                emitGameNameParsed(gameName!!)
            }

            if (parsingGameCode) {
                val parts = textStringBuilder.toString().split(' ')
                gameCode = parts[0]
                gameChecksum = parts[1]
                parsingGameCode = false
            }

            if (parsingFolder && parsingFolderName) {
                folderName = textStringBuilder.toString()
                parsingFolderName = false
            }

            if (parsingCheat && parsingCheatName) {
                cheatName = textStringBuilder.toString()
                parsingCheatName = false
            }

            if (parsingCheat && parsingCheatDescription) {
                val descriptionString = textStringBuilder.toString()
                if (descriptionString.isNotBlank()) {
                    cheatDescription = descriptionString
                }
                parsingCheatDescription = false
            }

            if (parsingCheat && parsingCheatCodes) {
                cheatCodes = textStringBuilder.toString()
                parsingCheatCodes = false
            }

            parsingText = false
        }

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
                emitGame(Game(null, gameName!!, gameCode!!, gameChecksum, ArrayList(currentGameFolders)))
            }
            currentGameFolders.clear()
            gameName = null
        }
    }

    override fun characters(ch: CharArray?, start: Int, length: Int) {
        if (parsingText && ch != null) {
            textStringBuilder.append(ch, 0, length)
        }
    }

    override fun endDocument() {
        listener.onParseComplete()
    }

    private fun emitCheatDatabaseName(databaseName: String) {
        listener.onCheatDatabaseParseStart(databaseName)
    }

    private fun emitGameNameParsed(gameName: String) {
        listener.onGameParseStart(gameName)
    }

    private fun emitGame(game: Game) {
        listener.onGameParsed(game)
    }
}