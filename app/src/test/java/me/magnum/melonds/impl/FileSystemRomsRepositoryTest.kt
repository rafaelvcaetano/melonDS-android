package me.magnum.melonds.impl

import android.app.Application
import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ProviderInfo
import android.database.Cursor
import android.database.MatrixCursor
import android.graphics.Bitmap
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract
import androidx.documentfile.provider.DocumentFile
import androidx.test.core.app.ApplicationProvider
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import me.magnum.melonds.common.romprocessors.RomFileProcessor
import me.magnum.melonds.common.romprocessors.RomFileProcessorFactory
import me.magnum.melonds.domain.model.RomInfo
import me.magnum.melonds.domain.model.RomScanningStatus
import me.magnum.melonds.domain.model.rom.Rom
import me.magnum.melonds.domain.model.rom.config.RomConfig
import me.magnum.melonds.domain.repositories.SettingsRepository
import me.magnum.melonds.impl.dtos.rom.RomDto
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowContentResolver
import java.io.File
import java.lang.reflect.Proxy
import java.util.Date
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.CoroutineContext
import kotlin.time.Duration.Companion.minutes

/** SAF enumeration/opening are real resolver calls; the tiny processor isolates scan ownership from NDS parsing. */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [28])
class FileSystemRomsRepositoryTest {
    @get:Rule val temporaryFolder = TemporaryFolder()
    private lateinit var context: Context
    private lateinit var provider: RomDocumentsProvider
    private lateinit var dispatcher: RecordingDispatcher
    private lateinit var repository: FileSystemRomsRepository
    private val gates = mutableListOf<ScanGate>()
    private val gson = Gson()
    private val treeUri = DocumentsContract.buildTreeDocumentUri(AUTHORITY, "roms")
    private var beforeRead: (Uri) -> Unit = {}

    @Before fun setUp() {
        val app = ApplicationProvider.getApplicationContext<Context>()
        val files = temporaryFolder.newFolder("private")
        context = object : ContextWrapper(app) { override fun getFilesDir() = files }
        provider = RomDocumentsProvider(temporaryFolder.newFolder("documents"))
        provider.attachInfo(context, ProviderInfo().apply { authority = AUTHORITY })
        ShadowContentResolver.registerProviderInternal(AUTHORITY, provider)
        dispatcher = RecordingDispatcher()
        val settings = Proxy.newProxyInstance(SettingsRepository::class.java.classLoader, arrayOf(SettingsRepository::class.java)) { _, method, _ ->
            when (method.name) {
                "observeRomSearchDirectories" -> emptyFlow<Array<Uri>>()
                "getRomSearchDirectories" -> arrayOf(treeUri)
                else -> error("Unexpected settings call: ${method.name}")
            }
        } as SettingsRepository
        val processor = object : RomFileProcessor {
            override fun getRomFromUri(romUri: Uri, parentUri: Uri?): Rom {
                beforeRead(romUri)
                val title = context.contentResolver.openInputStream(romUri)!!.bufferedReader().use { it.readText() }
                return rom(DocumentsContract.getDocumentId(romUri), title)
            }
            override fun getRomIcon(rom: Rom): Bitmap? = error("unused")
            override fun getRomInfo(rom: Rom): RomInfo? = error("unused")
            override suspend fun getRealRomUri(rom: Rom): Uri? = error("unused")
        }
        val factory = object : RomFileProcessorFactory {
            override fun getFileRomProcessorForDocument(romDocument: DocumentFile) = processor
            override fun getFileRomProcessorForDocument(romUri: Uri) = processor
        }
        repository = FileSystemRomsRepository(context, gson, settings, factory, dispatcher)
    }

    @After fun tearDown() = runBlocking {
        gates.forEach { it.release.countDown() }
        // The app singleton has no lifecycle API. Cancel and join it only for fixture cleanup.
        if (::repository.isInitialized) {
            val scopeField = FileSystemRomsRepository::class.java.getDeclaredField("coroutineScope").apply { isAccessible = true }
            val scope = scopeField.get(repository) as CoroutineScope
            scope.cancel()
            withTimeout(5_000) { scope.coroutineContext[Job]!!.join() }
        }
        if (::dispatcher.isInitialized) dispatcher.close()
    }

    @Test fun scanFailureRestoresIdleAndAllowsRetry() = runBlocking {
        provider.add("cached.nds", "Cached")
        writeCache(rom("cached.nds", "Cached"))
        provider.failOpen = true // permission is lost after the document is successfully enumerated
        repository.getRoms().first()
        dispatcher.awaitIdle()

        assertEquals(RomScanningStatus.NOT_SCANNING, repository.getRomScanningStatus().value)
        assertEquals(listOf("Cached"), repository.getRoms().first().map { it.name })
        provider.failOpen = false
        provider.add("new.nds", "New")
        repository.rescanRoms()
        dispatcher.awaitIdle()
        assertEquals(setOf("Cached", "New"), repository.getRoms().first().map { it.name }.toSet())
        assertEquals(3, provider.openCount.get()) // failed initial open, then both files on retry
        assertEquals(2, provider.childrenQueryCount.get())
    }

    @Test fun revokedCachedMetadataQueryPreservesSettingsForRetry() = runBlocking {
        provider.add("cached.nds", "Updated title")
        val cached = rom("cached.nds", "Cached").copy(config = RomConfig(customName = "Keep me"), lastPlayed = Date(321_000), totalPlayTime = 11.minutes)
        writeCache(cached)
        provider.failQuery = true
        val snapshot = repository.getRoms().first()
        dispatcher.awaitIdle()
        assertEquals("Access failure does not prove deletion", listOf(cached), snapshot)
        assertEquals(RomScanningStatus.NOT_SCANNING, repository.getRomScanningStatus().value)

        provider.failQuery = false
        repository.rescanRoms()
        dispatcher.awaitIdle()
        assertEquals(cached.copy(name = "Updated title"), repository.getRoms().first().single())
    }

    @Test fun confirmedDeletedCachedRomIsRemoved() = runBlocking {
        writeCache(rom("deleted.nds", "Deleted"))
        val snapshot = repository.getRoms().first()
        dispatcher.awaitIdle()
        assertTrue(snapshot.isEmpty())
        assertTrue(repository.getRoms().first().isEmpty())
        assertEquals(0, provider.openCount.get())
    }

    @Test fun cachedRomsRemainUsableDuringSlowScan() = runBlocking {
        provider.add("cached.nds", "Cached")
        val cached = rom("cached.nds", "Cached").copy(config = RomConfig(customName = "My game"), lastPlayed = Date(123_000), totalPlayTime = 7.minutes)
        writeCache(cached)
        val gate = scanGate()
        beforeRead = { gate.block() }

        val snapshot = withTimeout(5_000) { repository.getRoms().first() }
        gate.awaitEntry()
        assertEquals(listOf(cached), snapshot)
        assertEquals(snapshot, repository.getRoms().first())
        assertEquals(RomScanningStatus.SCANNING, repository.getRomScanningStatus().value)
        assertEquals(0, provider.openCount.get())
        assertEquals(1, provider.childrenQueryCount.get())
        gate.release.countDown()
        dispatcher.awaitIdle()
        assertEquals(listOf(cached), repository.getRoms().first())
    }

    @Test fun overlappingRefreshDoesNotReportIdleWhileScanning() = runBlocking {
        provider.add("cached.nds", "Cached")
        repository.getRoms().first()
        dispatcher.awaitIdle()
        val firstScan = scanGate()
        val reads = AtomicInteger()
        beforeRead = { if (reads.incrementAndGet() == 1) firstScan.block() }
        repository.rescanRoms()
        firstScan.awaitEntry()
        val completedBeforeRefresh = dispatcher.completed.get()
        repository.rescanRoms()
        // A concurrent implementation finishes scan 2; a serialized one suspends it on the lock.
        dispatcher.awaitCompleted(completedBeforeRefresh + 1)
        assertEquals("The first refresh still owns a blocked read", RomScanningStatus.SCANNING, repository.getRomScanningStatus().value)
        firstScan.release.countDown()
        dispatcher.awaitIdle()
        assertEquals(RomScanningStatus.NOT_SCANNING, repository.getRomScanningStatus().value)
    }

    @Test fun previouslyEmittedListRemainsStableAfterNewRom() = runBlocking {
        provider.add("cached.nds", "Cached")
        writeCache(rom("cached.nds", "Cached"))
        val previous = repository.getRoms().first()
        dispatcher.awaitIdle()
        provider.add("new.nds", "New")
        repository.rescanRoms()
        dispatcher.awaitIdle()

        assertEquals(setOf("Cached", "New"), repository.getRoms().first().map { it.name }.toSet())
        assertEquals("An earlier emission is a snapshot", listOf("Cached"), previous.map { it.name })
    }

    @Test fun metadataRefreshPreservesConfigAndUsage() = runBlocking {
        provider.add("cached.nds", "Updated title")
        val cached = rom("cached.nds", "Old title").copy(config = RomConfig(customName = "Keep me"), lastPlayed = Date(321_000), totalPlayTime = 11.minutes)
        writeCache(cached)
        repository.getRoms().first()
        dispatcher.awaitIdle()

        assertEquals(cached.copy(name = "Updated title"), repository.getRoms().first().single())
        assertEquals(1, provider.openCount.get())
        assertEquals(1, provider.childrenQueryCount.get())
    }

    @Test fun previouslyEmittedRomRemainsStableAfterConfigAndUsageChanges() = runBlocking {
        provider.add("cached.nds", "Cached")
        repository.getRoms().first()
        dispatcher.awaitIdle()
        val previous = repository.getRoms().first().single()
        val original = previous.copy()
        repository.updateRomConfig(previous, RomConfig(customName = "Personal title"))
        repository.setRomLastPlayed(previous, Date(999_000))
        repository.addRomPlayTime(previous, 2.minutes)

        val current = repository.getRoms().first().single()
        assertEquals("Personal title", current.config.customName)
        assertEquals(Date(999_000), current.lastPlayed)
        assertEquals(2.minutes, current.totalPlayTime)
        assertEquals(original, previous)
    }

    @Test fun cyclicProviderDirectoryIsVisitedOnceAndScanCompletes() = runBlocking {
        provider.cyclicDirectory = true

        repository.getRoms().first()
        dispatcher.awaitIdle()

        assertEquals(RomScanningStatus.NOT_SCANNING, repository.getRomScanningStatus().value)
        assertTrue("cycle was queried ${provider.childrenQueryCount.get()} times", provider.childrenQueryCount.get() <= 2)
        assertTrue(dispatcher.uncaught.isEmpty())
    }

    @Test fun nullNameAndEqualSizeCannotSubstituteAnUnrelatedCachedRom() = runBlocking {
        provider.add("cached.nds", "AAAA")
        writeCache(rom("cached.nds", "Cached"))
        repository.getRoms().first()
        dispatcher.awaitIdle()

        provider.add("incoming.nds", "BBBB")
        provider.nullDisplayNames += "incoming.nds"
        val incoming = DocumentsContract.buildDocumentUriUsingTree(treeUri, "incoming.nds")

        val resolved = repository.getRomAtUri(incoming)

        assertEquals(incoming, resolved?.uri)
        assertEquals("BBBB", resolved?.name)
    }

    private fun rom(id: String, name: String) = Rom(name, "Fixture", id,
        DocumentsContract.buildDocumentUriUsingTree(treeUri, id), treeUri, RomConfig.default(), null, false, "")

    private fun writeCache(vararg roms: Rom) {
        File(context.filesDir, "rom_data.json").writeText(gson.toJson(roms.map(RomDto::fromModel)))
    }

    private fun scanGate() = ScanGate().also { gates += it }

    private class ScanGate {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        fun block() { entered.countDown(); check(release.await(5, TimeUnit.SECONDS)) { "Scan gate timed out" } }
        fun awaitEntry() { assertTrue("Scan did not reach its read", entered.await(5, TimeUnit.SECONDS)) }
    }

    private class RecordingDispatcher : CoroutineDispatcher(), AutoCloseable {
        private val monitor = Object()
        private val submitted = AtomicInteger()
        val completed = AtomicInteger()
        val uncaught = CopyOnWriteArrayList<Throwable>()
        private val executor = Executors.newFixedThreadPool(2) { task ->
            Thread(task, "rom-scan-test").apply { uncaughtExceptionHandler = Thread.UncaughtExceptionHandler { _, error -> uncaught += error } }
        }

        override fun dispatch(context: CoroutineContext, block: Runnable) {
            submitted.incrementAndGet()
            executor.execute {
                try { block.run() } finally {
                    synchronized(monitor) { completed.incrementAndGet(); monitor.notifyAll() }
                }
            }
        }

        fun awaitCompleted(count: Int) = awaitCondition { completed.get() >= count }
        fun awaitIdle() = awaitCondition { completed.get() == submitted.get() }
        private fun awaitCondition(condition: () -> Boolean) {
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
            synchronized(monitor) {
                while (!condition()) {
                    val remaining = deadline - System.nanoTime()
                    assertTrue("Scan dispatcher did not finish: $uncaught", remaining > 0)
                    TimeUnit.NANOSECONDS.timedWait(monitor, remaining)
                }
            }
        }
        override fun close() {
            executor.shutdownNow()
            assertTrue("Scan executor leaked", executor.awaitTermination(5, TimeUnit.SECONDS))
        }
    }

    private class RomDocumentsProvider(private val directory: File) : ContentProvider() {
        private val files = ConcurrentHashMap<String, File>()
        val childrenQueryCount = AtomicInteger()
        val metadataQueryCount = AtomicInteger()
        val openCount = AtomicInteger()
        @Volatile var failOpen = false
        @Volatile var failQuery = false
        @Volatile var cyclicDirectory = false
        val nullDisplayNames = ConcurrentHashMap.newKeySet<String>()
        fun add(name: String, title: String) { files[name] = File(directory, name).apply { writeText(title) } }
        override fun onCreate() = true
        override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor {
            if (failQuery) throw SecurityException("Fixture permission revoked before metadata query")
            val children = uri.lastPathSegment == "children"
            if (children) childrenQueryCount.incrementAndGet() else metadataQueryCount.incrementAndGet()
            val columns = projection ?: arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
            val requestedId = if (DocumentsContract.isTreeUri(uri) && uri.pathSegments.size == 2) {
                DocumentsContract.getTreeDocumentId(uri)
            } else {
                DocumentsContract.getDocumentId(uri)
            }
            val ids = if (children) {
                (files.keys + if (cyclicDirectory) setOf("roms") else emptySet()).sorted()
            } else {
                listOf(requestedId).filter { files.containsKey(it) || (cyclicDirectory && it == "roms") }
            }
            return MatrixCursor(columns).apply {
                ids.forEach { id -> addRow(columns.map { column ->
                    when (column) {
                        DocumentsContract.Document.COLUMN_DOCUMENT_ID -> id
                        DocumentsContract.Document.COLUMN_DISPLAY_NAME -> id.takeUnless { it in nullDisplayNames }
                        DocumentsContract.Document.COLUMN_MIME_TYPE -> if (cyclicDirectory && id == "roms") DocumentsContract.Document.MIME_TYPE_DIR else "application/octet-stream"
                        DocumentsContract.Document.COLUMN_SIZE -> files[id]?.length() ?: 0L
                        DocumentsContract.Document.COLUMN_FLAGS -> 0
                        DocumentsContract.Document.COLUMN_LAST_MODIFIED -> 0L
                        else -> null
                    }
                }) }
            }
        }
        override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
            openCount.incrementAndGet()
            if (failOpen) throw SecurityException("Fixture permission revoked after enumeration")
            return ParcelFileDescriptor.open(files.getValue(DocumentsContract.getDocumentId(uri)), ParcelFileDescriptor.MODE_READ_ONLY)
        }
        override fun getType(uri: Uri) = "application/octet-stream"
        override fun insert(uri: Uri, values: ContentValues?): Uri? = error("unused")
        override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = error("unused")
        override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = error("unused")
    }

    companion object { private const val AUTHORITY = "me.magnum.melonds.rom-scan-test" }
}
