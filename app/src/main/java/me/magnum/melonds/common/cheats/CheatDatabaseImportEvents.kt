package me.magnum.melonds.common.cheats

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.runInterruptible
import me.magnum.melonds.domain.model.CheatDatabase
import me.magnum.melonds.domain.model.CheatImportEvent
import me.magnum.melonds.domain.model.Game

/** Own [stream] until parsing ends; keep blocking work off Room's transaction executor.
 * Only a few games can wait in memory. Cancellation closes the input before waiting
 * for the parser, including providers whose read does not respond to interruption. */
fun CheatDatabaseParser.importEvents(
    stream: ProgressTrackerInputStream,
    onGameStart: (String) -> Unit,
): Flow<CheatImportEvent> = channelFlow {
    fun emit(event: CheatImportEvent) { trySendBlocking(event).getOrThrow() }
    val listener = object : CheatDatabaseParserListener {
        override fun onDatabaseParseStart(databaseName: String): CheatDatabase {
            emit(CheatImportEvent.Start(databaseName))
            return CheatDatabase(0, databaseName) // Replaced with the persisted ID by the repository.
        }
        override fun onGameParseStart(gameName: String) = onGameStart(gameName)
        override fun onGameParsed(game: Game) = emit(CheatImportEvent.ParsedGame(game))
        override fun onParseComplete() = emit(CheatImportEvent.Complete)
    }
    stream.use {
        val parsing = async { runInterruptible { parseCheatDatabase(stream, listener) } }
        try {
            parsing.await()
        } catch (error: Throwable) {
            // Release callbacks blocked in trySendBlocking as well as blocked reads.
            channel.close(error)
            throw error
        }
        // use closes here, before channelFlow joins the parser child on cancellation.
    }
}.buffer(4).flowOn(Dispatchers.IO)
