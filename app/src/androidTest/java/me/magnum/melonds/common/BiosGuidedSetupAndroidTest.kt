package me.magnum.melonds.common

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.provider.DocumentsContract
import androidx.documentfile.provider.DocumentFile
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import me.magnum.melonds.domain.model.BiosSlot
import me.magnum.melonds.domain.model.FirmwareConsoleType
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Real SAF tree operations across Binder; all source bytes are synthetic test data. */
@RunWith(AndroidJUnit4::class)
class BiosGuidedSetupAndroidTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val authority by lazy {
        @Suppress("DEPRECATION")
        val info = instrumentation.context.packageManager.getPackageInfo(
            instrumentation.context.packageName, PackageManager.GET_PROVIDERS,
        )
        requireNotNull(info.providers).single { it.name == BiosGuidedSetupTestProvider::class.java.name }.authority
    }
    private val controlUri get() = Uri.parse("content://$authority")
    private val roots = mutableListOf<String>()
    private val setup by lazy { BiosGuidedFolderSetup(context) }

    @Before fun accessTestProvider() {
        bootstrapGrant(BiosGuidedSetupTestProvider.GrantReceiver.GRANT)
    }

    @After fun removeFixtures() {
        try { roots.forEach { control("remove", it) } }
        finally { bootstrapGrant(BiosGuidedSetupTestProvider.GrantReceiver.REVOKE) }
    }

    private fun bootstrapGrant(action: String) {
        val replied = CountDownLatch(1)
        var replyCode = Activity.RESULT_CANCELED
        var replyData: String? = null
        val intent = Intent(action).apply {
            component = ComponentName(instrumentation.context.packageName,
                BiosGuidedSetupTestProvider.GrantReceiver::class.java.name)
            addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
        }
        context.sendOrderedBroadcast(intent, null, object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                replyCode = resultCode
                replyData = resultData
                replied.countDown()
            }
        }, Handler(Looper.getMainLooper()), Activity.RESULT_CANCELED, null, null)
        assertTrue("Provider URI grant receiver must reply", replied.await(5, TimeUnit.SECONDS))
        assertEquals("Provider URI grant result: $replyData", Activity.RESULT_OK, replyCode)
        assertEquals("Grant recipient must be the fixed instrumentation target", context.packageName, replyData)
    }

    @Test(timeout = 20000)
    fun completeCopyPublishesAfterStreamCloseAndPreservesSource() = runBlocking {
        val bytes = synthetic(131072).apply {
            this[FirmwareConsoleType.HEADER_OFFSET] = FirmwareConsoleType.DSI_HEADER_BYTE.toByte()
        }
        val tree = fixture("normal", "unlabelled-firmware.bin", bytes)

        val report = requireNotNull(setup.runCancellable(tree.uri))

        assertEquals(BiosGuidedFolderSetup.Outcome.CREATED,
            report.slotResults.single { it.slot == BiosSlot.DSI_FIRMWARE }.outcome)
        val directory = requireNotNull(report.dsiDirectory)
        assertArrayEquals(bytes, read(requireNotNull(directory.findFile("firmware.bin"))))
        assertArrayEquals(bytes, read(requireNotNull(tree.findFile("unlabelled-firmware.bin"))))
        assertEquals(listOf("firmware.bin"), directory.listFiles().map { it.name })
        val status = status(tree)
        assertEquals(1, status.getInt("renames"))
        assertEquals(1, status.getInt("published"))
        assertEquals("Copy must start under a temporary name", 0, status.getInt("canonicalCreates"))
        assertEquals("Publication requires closed source and output descriptors", 0, status.getInt("publishBeforeClose"))
    }

    @Test(timeout = 20000)
    fun renameRefusalReportsErrorAndRemovesOnlyTheStagedCopy() {
        val bytes = synthetic(16384)
        val tree = fixture("rename-refusal", "loose-arm7.bin", bytes)

        val report = requireNotNull(setup.run(tree.uri))

        assertEquals(BiosGuidedFolderSetup.Outcome.ERROR,
            report.slotResults.single { it.slot == BiosSlot.DS_BIOS7 }.outcome)
        assertTrue(requireNotNull(report.dsDirectory).listFiles().isEmpty())
        assertArrayEquals(bytes, read(requireNotNull(tree.findFile("loose-arm7.bin"))))
        assertEquals(1, status(tree).getInt("renames"))
        assertEquals(0, status(tree).getInt("published"))
    }

    @Test(timeout = 20000)
    fun refusedCleanupLeavesNamedTemporaryAndRetryCopiesTheWholeSource() = runBlocking {
        val bytes = synthetic(16384)
        val tree = fixture("open-delete-refusal", "loose-arm7.bin", bytes)
        val failed = requireNotNull(setup.runCancellable(tree.uri))
        val result = failed.slotResults.single { it.slot == BiosSlot.DS_BIOS7 }
        val directory = requireNotNull(failed.dsDirectory)

        assertEquals(BiosGuidedFolderSetup.Outcome.CLEANUP_FAILED, result.outcome)
        val leftoverName = requireNotNull(result.cleanupFileName)
        assertTrue(leftoverName.startsWith("bios7.bin.melonds-"))
        assertTrue(leftoverName.endsWith(".part"))
        assertNull(directory.findFile("bios7.bin"))
        assertEquals(listOf(leftoverName), directory.listFiles().map { it.name })
        assertArrayEquals(byteArrayOf(), read(requireNotNull(directory.findFile(leftoverName))))
        assertArrayEquals(bytes, read(requireNotNull(tree.findFile("loose-arm7.bin"))))

        control("normal", id(tree))
        val retried = requireNotNull(setup.runCancellable(tree.uri))

        assertEquals(BiosGuidedFolderSetup.Outcome.CREATED,
            retried.slotResults.single { it.slot == BiosSlot.DS_BIOS7 }.outcome)
        assertArrayEquals(bytes, read(requireNotNull(directory.findFile("bios7.bin"))))
        assertArrayEquals(bytes, read(requireNotNull(tree.findFile("loose-arm7.bin"))))
        assertArrayEquals("A later run must not delete another run's leftover", byteArrayOf(),
            read(requireNotNull(directory.findFile(leftoverName))))
        assertEquals(setOf(leftoverName, "bios7.bin"), directory.listFiles().map { it.name }.toSet())
        assertEquals(0, status(tree).getInt("canonicalCreates"))
    }

    @Test(timeout = 25000)
    fun cancellationAfterPartialNandWriteCleansUpAfterPipeRelease() = runBlocking {
        val bytes = synthetic(32768)
        val tree = fixture("blocked-nand", "nand.bin", bytes)
        val job = launch(Dispatchers.IO) { setup.runCancellable(tree.uri) }
        try {
            withTimeout(5000) {
                while (status(tree).getLong("partialBytes") == 0L) delay(20)
            }
            assertTrue(status(tree).getBoolean("pipeStarted"))
            assertTrue(status(tree).getLong("partialBytes") < bytes.size)
            val directory = requireNotNull(tree.findFile("DSi"))
            assertNull("A partial NAND must never use the canonical name", directory.findFile("nand.bin"))
            job.cancel()
            // Cooperative cancellation needs the withheld read to return. No immediate-stop claim.
            control("release", id(tree))
            withTimeout(5000) { job.join() }
            assertTrue(job.isCancelled)
            assertTrue("The owned partial is removed after cancellation", directory.listFiles().isEmpty())
            assertArrayEquals(bytes, read(requireNotNull(tree.findFile("nand.bin"))))
            assertEquals(0, status(tree).getInt("renames"))
        } finally {
            control("release", id(tree))
            job.cancelAndJoin()
        }
    }

    private fun synthetic(size: Int) = ByteArray(size) { ((it * 17 + 3) and 255).toByte() }

    private fun fixture(mode: String, name: String, bytes: ByteArray): DocumentFile {
        val response = requireNotNull(context.contentResolver.call(controlUri, "fixture-register", mode,
            Bundle().apply { putString("name", name); putByteArray("bytes", bytes) }))
        assertNotEquals("The provider must run in a separate process", Process.myPid(), response.getInt("pid"))
        val id = requireNotNull(response.getString("id"))
        roots += id
        return requireNotNull(DocumentFile.fromTreeUri(context, DocumentsContract.buildTreeDocumentUri(authority, id)))
    }

    private fun id(tree: DocumentFile) = DocumentsContract.getTreeDocumentId(tree.uri)
    private fun status(tree: DocumentFile) = control("status", id(tree))
    private fun control(method: String, id: String): Bundle =
        requireNotNull(context.contentResolver.call(controlUri, "fixture-$method", id, null))
    private fun read(file: DocumentFile): ByteArray =
        requireNotNull(context.contentResolver.openInputStream(file.uri)).use { it.readBytes() }
}
