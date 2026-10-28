package me.magnum.melonds.common.workers

import android.content.Context
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.flow
import me.magnum.melonds.common.cheats.CloseAwareBlockingInputStream
import me.magnum.melonds.common.cheats.importEvents
import me.magnum.melonds.domain.model.CheatImportEvent
import me.magnum.melonds.common.cheats.CheatDatabaseParser
import me.magnum.melonds.common.cheats.XmlCheatDatabaseParser
import me.magnum.melonds.common.cheats.UsrCheatDatabaseParser
import me.magnum.melonds.common.cheats.UsrCheatFixture
import me.magnum.melonds.common.cheats.ProgressTrackerInputStream
import me.magnum.melonds.common.cheats.CheatDatabaseParserListener
import me.magnum.melonds.database.MelonDatabase
import me.magnum.melonds.database.entities.CheatDatabaseEntity
import me.magnum.melonds.domain.model.Cheat
import me.magnum.melonds.domain.model.CheatFolder
import me.magnum.melonds.domain.model.Game
import me.magnum.melonds.impl.RoomCheatsRepository
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.File
import java.lang.reflect.InvocationTargetException
import kotlin.coroutines.Continuation
import kotlin.coroutines.intrinsics.suspendCoroutineUninterceptedOrReturn

/** Real parsers, ContentResolver file I/O, worker import path and Room/SQLite storage.
 * WorkManager scheduling/notification delivery is the only framework test adapter. */
abstract class CheatImportIntegrityContract {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var database: MelonDatabase
    private lateinit var repository: RoomCheatsRepository
    private lateinit var worker: CheatImportWorker
    private val files = mutableListOf<File>()

    @Before fun setUp() = runBlocking {
        database = Room.inMemoryDatabaseBuilder(context, MelonDatabase::class.java).build()
        repository = RoomCheatsRepository(context, database)
        database.cheatDatabaseDao().insertCheatDatabase(CheatDatabaseEntity(0, "__custom_cheat_database"))
        val old = repository.addCheatDatabase("Original database")
        repository.addGameCheats(Game(null, "Original game", "OLD1", "12345678", listOf(
            CheatFolder(null, "Original folder", listOf(Cheat(null, old.id!!, "Old code", null, "02000000 00000001", false))),
            CheatFolder(null, "My folder", listOf(Cheat(null, 0, "My code", "Keep me", "02000000 00000002", true))),
        )))
        database.openHelper.writableDatabase.execSQL("UPDATE cheat SET enabled = 1 WHERE cheat_database_id = 0")
        worker = TestListenableWorkerBuilder<CheatImportWorker>(context).setWorkerFactory(object : WorkerFactory() {
            override fun createWorker(appContext: Context, workerClassName: String, workerParameters: WorkerParameters): ListenableWorker =
                CheatImportWorker(appContext, workerParameters, repository)
        }).build()
    }
    @After fun close() { database.close(); files.forEach { it.delete() } }

    private fun file(content: String): File = File.createTempFile("cheat-import-", ".xml", context.cacheDir).also {
        it.writeText(content); files += it
    }
    // Invoke the production suspend import path without substituting its listener or database.
    private suspend fun import(file: File, parser: CheatDatabaseParser = XmlCheatDatabaseParser()): Any? =
        suspendCoroutineUninterceptedOrReturn { continuation ->
            val method = CheatImportWorker::class.java.getDeclaredMethod("parseCheatDatabase", Uri::class.java, CheatDatabaseParser::class.java, Continuation::class.java)
            method.isAccessible = true
            try { method.invoke(worker, Uri.fromFile(file), parser, continuation) }
            catch (e: InvocationTargetException) { throw e.targetException }
        }
    private fun codes(): List<String> = database.openHelper.readableDatabase.query("SELECT code FROM cheat ORDER BY code").use { cursor ->
        buildList { while (cursor.moveToNext()) add(cursor.getString(0)) }
    }
    private fun snapshot(): List<String> = listOf("cheat_database", "game", "cheat_folder", "cheat").flatMap { table ->
        database.openHelper.readableDatabase.query("SELECT * FROM $table ORDER BY id").use { cursor ->
            buildList {
                while (cursor.moveToNext()) add(table + ":" + (0 until cursor.columnCount).joinToString("|") { cursor.getString(it) ?: "<null>" })
            }
        }
    }
    private val newGame = """<game><name>New game</name><gameid>NEW1 87654321</gameid><folder><name>New folder</name><cheat><name>New code</name><codes>02000000 00000003</codes></cheat></folder></game>"""

    @Test fun malformedXmlPreservesExistingImportedAndCustomCheats() = runBlocking {
        val original = snapshot()
        var failed = false
        try { import(file("<codelist><name>Replacement</name>$newGame<broken>")) }
        catch (_: Exception) { failed = true }
        assertTrue("Malformed input must be rejected", failed)
        assertEquals(original, snapshot())
        assertEquals(listOf("Original game"), repository.getGames().map { it.name })
    }

    @Test fun validXmlReplacesImportedCheatsAndPreservesCustomCheats() = runBlocking {
        assertEquals(ListenableWorker.Result.success(), import(file("<codelist><name>Replacement</name>$newGame</codelist>")))
        assertEquals(listOf("02000000 00000002", "02000000 00000003"), codes())
    }

    @Test fun malformedDatPreservesExistingCheats() = runBlocking {
        val original = snapshot()
        val source = file("").apply { writeBytes(UsrCheatFixture.bytes().copyOf(350)) }
        var failed = false
        try { import(source, UsrCheatDatabaseParser()) } catch (_: Exception) { failed = true }
        assertTrue(failed)
        assertEquals(original, snapshot())
    }

    @Test fun validDatReplacesImportedCheatsAndPreservesCustomCheats() = runBlocking {
        val source = file("").apply { writeBytes(UsrCheatFixture.bytes()) }
        assertEquals(ListenableWorker.Result.success(), import(source, UsrCheatDatabaseParser()))
        assertEquals(listOf("02000000 00000001", "02000000 00000002"), codes())
        assertTrue(repository.getGames().any { it.gameCode == "TEST" })
    }

    @Test fun missingDatabaseHeaderIsRejectedWithoutMutation() = runBlocking {
        val original = snapshot()
        var failed = false
        try { import(file("<unrelated/>")) } catch (_: Exception) { failed = true }
        assertTrue("Not a cheat database", failed)
        assertEquals(original, snapshot())
    }

    @Test fun sqliteWriteFailureRollsBackReplacement() = runBlocking {
        val original = snapshot()
        database.openHelper.writableDatabase.execSQL("CREATE TRIGGER fail_import BEFORE INSERT ON cheat WHEN NEW.code = '02000000 00000003' BEGIN SELECT RAISE(ABORT, 'Injected storage failure'); END")
        var failed = false
        try { import(file("<codelist><name>Replacement</name>$newGame</codelist>")) } catch (_: Exception) { failed = true }
        assertTrue(failed)
        assertEquals(original, snapshot())
    }

    @Test fun parserCancellationRollsBackReplacement() = runBlocking {
        val original = snapshot()
        val parser = object : CheatDatabaseParser {
            override fun parseCheatDatabase(databaseStream: ProgressTrackerInputStream, parseListener: CheatDatabaseParserListener) {
                parseListener.onDatabaseParseStart("Cancelled replacement")
                throw CancellationException("Cancelled during parsing")
            }
        }
        var cancelled = false
        try { import(file(""), parser) } catch (_: CancellationException) { cancelled = true }
        assertTrue(cancelled)
        assertEquals(original, snapshot())
    }

    @Test fun consumerCancellationRollsBackAllRows() = runBlocking {
        val original = snapshot()
        val transactionStarted = CompletableDeferred<Unit>()
        val job = launch {
            repository.replaceImportedCheats(flow {
                emit(CheatImportEvent.Start("Interrupted import"))
                transactionStarted.complete(Unit)
                awaitCancellation()
            })
        }
        withTimeout(5000) { transactionStarted.await() }
        job.cancelAndJoin()
        assertEquals(original, snapshot())
    }

    @Test(timeout = 15000)
    fun blockedDatCancellationRollsBackAndAllowsNextImport() = runBlocking {
        val original = snapshot()
        val source = CloseAwareBlockingInputStream(UsrCheatFixture.manyGames(100))
        val replacementStarted = CompletableDeferred<Unit>()
        val job = launch(Dispatchers.Default) {
            repository.replaceImportedCheats(flow {
                UsrCheatDatabaseParser().importEvents(ProgressTrackerInputStream(source)) {}.collect { event ->
                    emit(event)
                    // emit returns after the real repository processed Start inside its transaction.
                    if (event is CheatImportEvent.Start) replacementStarted.complete(Unit)
                }
            })
        }
        var stopped = false
        var closedBeforeCleanup = false
        try {
            withTimeout(5000) { replacementStarted.await() }
            assertTrue(withContext(Dispatchers.IO) { source.awaitRead() })
            job.cancel()
            stopped = withTimeoutOrNull(1000) { job.join(); true } == true
            closedBeforeCleanup = source.closed.get()
        } finally {
            source.close()
            job.cancelAndJoin()
        }
        assertTrue("Blocked import must release the read before external provider cleanup", closedBeforeCleanup)
        assertTrue("Cancelled transaction must finish promptly", stopped)
        assertEquals(original, snapshot())
        withTimeout(5000) {
            assertEquals(ListenableWorker.Result.success(), import(file("<codelist><name>Replacement</name>$newGame</codelist>")))
        }
        assertEquals(listOf("02000000 00000002", "02000000 00000003"), codes())
    }

    @Test fun datTruncatedAtGameOffsetCannotEraseImportedCheats() = runBlocking {
        val original = snapshot()
        val source = file("").apply { writeBytes(UsrCheatFixture.bytes().copyOf(288)) }
        var failed = false
        try { import(source, UsrCheatDatabaseParser()) } catch (_: Exception) { failed = true }
        assertTrue("Missing game data must not be accepted as an empty database", failed)
        assertEquals(original, snapshot())
    }

    @Test(timeout = 10000)
    fun sqliteFailureDuringRealDatParsingStopsProducerAndRollsBack() = runBlocking {
        val original = snapshot()
        val source = file("").apply { writeBytes(UsrCheatFixture.manyGames(100)) }
        database.openHelper.writableDatabase.execSQL("CREATE TRIGGER fail_dat_import BEFORE INSERT ON cheat WHEN NEW.name = 'Test' BEGIN SELECT RAISE(ABORT, 'Injected DAT storage failure'); END")
        var failed = false
        try { import(source, UsrCheatDatabaseParser()) } catch (_: Exception) { failed = true }
        assertTrue(failed)
        assertEquals(original, snapshot())
    }
}
