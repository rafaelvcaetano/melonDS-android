package me.magnum.melonds.common.workers

import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.Process
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.workDataOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
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
import org.junit.runner.RunWith

/** Real ContentResolver/Binder/PFD and Room. The builder substitutes WorkManager scheduling/notifications. */
@RunWith(AndroidJUnit4::class)
class CheatImportProviderCancellationTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val testContext = InstrumentationRegistry.getInstrumentation().context
    private val authority by lazy {
        @Suppress("DEPRECATION")
        val info = testContext.packageManager.getPackageInfo(testContext.packageName, PackageManager.GET_PROVIDERS)
        requireNotNull(info.providers).single { it.name == CheatImportTestProvider::class.java.name }.authority
    }
    private val controlUri get() = Uri.parse("content://$authority")
    private lateinit var database: MelonDatabase
    private lateinit var repository: RoomCheatsRepository
    private val fixtures = mutableListOf<String>()
    private val xml = """<codelist><name>Replacement</name><game><name>New game</name><gameid>NEW1 87654321</gameid><folder><name>New folder</name><cheat><name>New code</name><codes>02000000 00000003</codes></cheat></folder></game></codelist>""".toByteArray()

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
    }

    @After fun close() {
        fixtures.forEach { control("remove", it) }
        database.close()
    }

    private fun fixture(mode: String): Uri {
        val result = requireNotNull(context.contentResolver.call(controlUri, "register", mode,
            Bundle().apply { putByteArray("bytes", xml) }))
        assertNotEquals("The provider must run in another process", Process.myPid(), result.getInt("pid"))
        val id = requireNotNull(result.getString("id"))
        fixtures += id
        return controlUri.buildUpon().appendPath("document").appendPath(id).build()
    }

    private fun control(method: String, id: String): Bundle =
        requireNotNull(context.contentResolver.call(controlUri, method, id, null))

    private suspend fun await(method: String, uri: Uri): Boolean = withContext(Dispatchers.IO) {
        control(method, requireNotNull(uri.lastPathSegment)).getBoolean("ready")
    }

    private fun worker(uri: Uri) = TestListenableWorkerBuilder<CheatImportWorker>(context)
        .setInputData(workDataOf(CheatImportWorker.KEY_URI to uri.toString()))
        .setWorkerFactory(object : WorkerFactory() {
            override fun createWorker(appContext: Context, workerClassName: String, workerParameters: WorkerParameters): ListenableWorker =
                CheatImportWorker(appContext, workerParameters, repository)
        }).build()

    private fun snapshot(): List<String> = listOf("cheat_database", "game", "cheat_folder", "cheat").flatMap { table ->
        database.openHelper.readableDatabase.query("SELECT * FROM $table ORDER BY id").use { cursor ->
            buildList {
                while (cursor.moveToNext()) add(table + ":" + (0 until cursor.columnCount).joinToString("|") { cursor.getString(it) ?: "<null>" })
            }
        }
    }

    private fun codes(): List<String> = database.openHelper.readableDatabase.query("SELECT code FROM cheat ORDER BY code").use { cursor ->
        buildList { while (cursor.moveToNext()) add(cursor.getString(0)) }
    }

    private suspend fun assertNextImport() {
        withTimeout(5000) {
            assertEquals(ListenableWorker.Result.success(), worker(fixture("valid")).doWork())
        }
        assertEquals(listOf("02000000 00000002", "02000000 00000003"), codes())
    }

    @Test(timeout = 20000)
    fun cancellationDuringProviderOpenStopsWorkerAndPreservesRoom() = runBlocking {
        val original = snapshot()
        val uri = fixture("cancel")
        val job = launch(Dispatchers.IO) { worker(uri).doWork() }
        var signalReceived = false
        var stopped = false
        try {
            assertTrue("Worker reached the provider's blocking open", await("await-open", uri))
            job.cancel()
            signalReceived = await("await-cancel", uri)
            stopped = withTimeoutOrNull(2000) { job.join(); true } == true
        } finally {
            control("release", requireNotNull(uri.lastPathSegment))
            job.cancelAndJoin()
        }
        assertTrue("Cancellation must cross Binder to the provider", signalReceived)
        assertTrue("Provider signal must stop the worker before fixture cleanup", stopped)
        assertEquals(0, control("status", requireNotNull(uri.lastPathSegment)).getInt("active"))
        assertEquals(original, snapshot())
        assertNextImport()
    }

    @Test(timeout = 20000)
    fun descriptorReturnedAfterCancellationClosesWithoutChangingRoom() = runBlocking {
        val original = snapshot()
        val uri = fixture("late")
        val id = requireNotNull(uri.lastPathSegment)
        val job = launch(Dispatchers.IO) { worker(uri).doWork() }
        var signalReceived = false
        var closed = false
        var stopped = false
        try {
            assertTrue("Worker reached the provider's blocking open", await("await-open", uri))
            job.cancel()
            signalReceived = await("await-cancel", uri)
            // The provider deliberately returns a real descriptor after cancellation.
            control("release", id)
            stopped = withTimeoutOrNull(2000) { job.join(); true } == true
            closed = await("await-close", uri)
        } finally {
            control("release", id)
            job.cancelAndJoin()
        }
        assertTrue("Cancellation must reach the acquisition while it is pending", signalReceived)
        assertTrue("The returned descriptor must be closed by the client", closed)
        assertTrue("Worker must finish after the late return", stopped)
        val status = control("status", id)
        assertTrue("Fixture returned after the remote signal was cancelled", status.getBoolean("returnedAfterCancel"))
        assertEquals(1, status.getInt("closes"))
        assertEquals(0, status.getInt("active"))
        assertEquals(original, snapshot())
        assertNextImport()
    }

    @Test(timeout = 10000)
    fun assetSliceImportsOnlyDeclaredBytes() = runBlocking {
        assertEquals(ListenableWorker.Result.success(), worker(fixture("slice")).doWork())
        assertEquals(listOf("02000000 00000002", "02000000 00000003"), codes())
    }

    @Test(timeout = 15000)
    fun cancellationClosesOffsetPipeBeforeProviderEof() = runBlocking {
        val original = snapshot()
        val uri = fixture("offset-pipe")
        val job = launch(Dispatchers.IO) { worker(uri).doWork() }
        var stopped = false
        try {
            // Framework versions differ in how they handle a pipe's nonzero start offset.
            // Observe the real blocking point; do not assume the constructor performs the skip.
            val blockedPhase = withTimeoutOrNull(5000) {
                var phase: String? = null
                while (phase == null) {
                    val stack = Thread.getAllStackTraces().values.firstOrNull { frames ->
                        frames.any { it.className.contains("CheatImportWorker") || it.className.contains("CheatDatabaseImportEvents") } &&
                            frames.any { it.className.startsWith("android.content.res.AssetFileDescriptor") }
                    }
                    if (stack != null) phase = if (stack.any { it.methodName == "createInputStream" }) "conversion" else "parser read"
                    else delay(10)
                }
                phase
            }
            assertNotNull("Worker reached the real offset pipe; completed=${job.isCompleted}", blockedPhase)
            android.util.Log.i("ImportProviderTest", "Offset pipe blocked in $blockedPhase")
            job.cancel()
            stopped = withTimeoutOrNull(2000) { job.join(); true } == true
        } finally {
            // Only fixture cleanup supplies EOF; the oracle above must finish without it.
            control("release", requireNotNull(uri.lastPathSegment))
            job.cancelAndJoin()
        }
        assertTrue("Cancellation must close the offset pipe without provider EOF", stopped)
        assertEquals(original, snapshot())
        assertNextImport()
    }

    @Test(timeout = 10000)
    fun unknownLengthPipeImportsSuccessfully() = runBlocking {
        assertEquals(ListenableWorker.Result.success(), worker(fixture("pipe")).doWork())
        assertEquals(listOf("02000000 00000002", "02000000 00000003"), codes())
    }

    @Test(timeout = 10000)
    fun providerOpenFailurePreservesRoomAndAllowsNextImport() = runBlocking {
        val original = snapshot()
        assertEquals(ListenableWorker.Result.failure(), worker(fixture("failure")).doWork())
        assertEquals(original, snapshot())
        assertNextImport()
    }
}
