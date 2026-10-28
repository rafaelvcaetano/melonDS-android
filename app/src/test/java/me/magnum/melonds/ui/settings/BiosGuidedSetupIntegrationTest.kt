package me.magnum.melonds.ui.settings

import android.Manifest
import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.ContentResolver
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.pm.ProviderInfo
import android.database.MatrixCursor
import android.database.Cursor
import android.net.Uri
import android.os.CancellationSignal
import android.os.Bundle
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract
import android.provider.DocumentsProvider
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.preference.PreferenceManager
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.serialization.json.Json
import me.magnum.melonds.R
import me.magnum.melonds.common.BiosGuidedFolderSetup
import me.magnum.melonds.common.DirectoryAccessValidator
import me.magnum.melonds.common.UriPermissionManager
import me.magnum.melonds.common.uridelegates.UriHandler
import me.magnum.melonds.domain.model.ConfigurationDirResult
import me.magnum.melonds.domain.model.ConsoleType
import me.magnum.melonds.impl.FileSystemConfigurationDirectoryVerifier
import me.magnum.melonds.impl.SharedPreferencesSettingsRepository
import me.magnum.melonds.impl.input.ControllerConfigurationFactory
import me.magnum.melonds.ui.settings.fragments.CustomFirmwarePreferencesFragment
import me.magnum.melonds.ui.settings.preferences.BiosDirectoryPickerPreference
import me.magnum.melonds.ui.settings.viewmodel.CustomFirmwareViewModel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import org.robolectric.annotation.Implements
import org.robolectric.annotation.Implementation
import org.robolectric.shadows.ShadowContentResolver
import org.robolectric.shadows.ShadowDialog
import org.robolectric.util.ReflectionHelpers
import java.io.File
import java.io.ByteArrayInputStream
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream
import java.lang.reflect.Proxy
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/** Real SAF document operations, console validation and persisted preferences under Robolectric. */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [28], shadows = [BiosGuidedSetupIntegrationTest.DocumentsResolver::class])
class BiosGuidedSetupIntegrationTest {
    @get:Rule val temporaryFolder = TemporaryFolder()
    private lateinit var context: Context
    private lateinit var preferences: SharedPreferences
    private lateinit var provider: BiosDocumentsProvider
    private lateinit var settings: SharedPreferencesSettingsRepository
    private lateinit var verifier: FileSystemConfigurationDirectoryVerifier
    private lateinit var controller: ActivityController<FragmentActivity>
    private lateinit var fragment: CustomFirmwarePreferencesFragment
    private val preferenceScope = CoroutineScope(SupervisorJob().apply { cancel() })

    @Before fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        preferences = PreferenceManager.getDefaultSharedPreferences(context)
        preferences.edit().clear().putBoolean("use_custom_bios", true).commit()
        provider = BiosDocumentsProvider(temporaryFolder.newFolder("documents"))
        val info = ProviderInfo().apply {
            authority = AUTHORITY
            name = BiosDocumentsProvider::class.java.name
            packageName = context.packageName
            applicationInfo = context.applicationInfo
            exported = true
            grantUriPermissions = true
            readPermission = Manifest.permission.MANAGE_DOCUMENTS
            writePermission = Manifest.permission.MANAGE_DOCUMENTS
        }
        val packageManager = Shadows.shadowOf(context.packageManager)
        packageManager.addOrUpdateProvider(info)
        packageManager.addIntentFilterForProvider(ComponentName(info.packageName, info.name), IntentFilter(DocumentsContract.PROVIDER_INTERFACE))
        provider.attachInfo(context, info)
        ShadowContentResolver.registerProviderInternal(AUTHORITY, provider)
        settings = SharedPreferencesSettingsRepository(context, preferences, unused<ControllerConfigurationFactory>(), Json, unused<UriHandler>(), preferenceScope)
        verifier = FileSystemConfigurationDirectoryVerifier(context, settings)
        createDs("old-ds")
        createDsi("old-dsi", nand = true)
        settings.setDsBiosDirectory(provider.treeUri("old-ds"))
        settings.setDsiBiosDirectory(provider.treeUri("old-dsi"))
        val oldDsStatus = verifier.checkDsConfigurationDirectory()
        assertEquals("Old DS fixture status: ${oldDsStatus.fileResults.toList()}", ConfigurationDirResult.Status.VALID, oldDsStatus.status)
        assertEquals(ConfigurationDirResult.Status.VALID, verifier.checkDsiConfigurationDirectory().status)
        provider.directory("incoming")

        controller = Robolectric.buildActivity(FragmentActivity::class.java)
        controller.get().setTheme(R.style.AppTheme)
        controller.setup()
        attachFirmwareFragment()
    }

    private fun attachFirmwareFragment() {
        fragment = CustomFirmwarePreferencesFragment()
        // Replace only Hilt wiring: the native Application cannot load its JNI library on the JVM.
        // Keep the real Fragment lifecycle, resource inflation, verifier, preferences and dialogs.
        ReflectionHelpers.setField(fragment, "injected", true)
        ReflectionHelpers.setField(fragment, "componentContext", controller.get())
        ReflectionHelpers.setField(fragment, "viewModel\$delegate", lazyOf(CustomFirmwareViewModel(verifier)))
        fragment.uriPermissionManager = UriPermissionManager(context)
        fragment.directoryAccessValidator = DirectoryAccessValidator(context)
        controller.get().supportFragmentManager.beginTransaction().add(android.R.id.content, fragment).commitNow()
    }

    @After fun tearDown() {
        ShadowDialog.getLatestDialog()?.dismiss()
        if (::controller.isInitialized) controller.pause().stop().destroy()
        preferenceScope.cancel()
    }

    @Test fun unreadableSourceSizeNamesTheFileAndOffersRetryWithoutGuessingItsSlot() {
        provider.file("incoming/locked.dump", 8192)
        provider.unreadableIds += "root/incoming/locked.dump"

        val report = setupIncoming()
        val dialog = applyReport(report)

        assertTrue("Unreadable source must be named in the result", message(dialog).contains("locked.dump"))
        assertEquals(View.VISIBLE, dialog.getButton(AlertDialog.BUTTON_NEUTRAL).visibility)
        assertEquals(context.getString(R.string.retry), dialog.getButton(AlertDialog.BUTTON_NEUTRAL).text.toString())
        assertTrue(report.slotResults.all { it.outcome != BiosGuidedFolderSetup.Outcome.CREATED })
        assertOldPreferences()
    }

    @Test fun unreadableFirmwareHeaderNamesTheFileAndOffersRetry() {
        provider.file("incoming/firmware-with-read-error.bin", 131072)
        Shadows.shadowOf(context.contentResolver).registerInputStream(provider.documentUri("incoming/firmware-with-read-error.bin"), object : InputStream() {
            override fun read(): Int = throw IOException("header read failed")
            override fun skip(n: Long): Long = throw IOException("header read failed")
        })

        val report = setupIncoming()
        val dialog = applyReport(report)

        assertTrue("Unreadable firmware must be named in the result", message(dialog).contains("firmware-with-read-error.bin"))
        assertEquals(View.VISIBLE, dialog.getButton(AlertDialog.BUTTON_NEUTRAL).visibility)
        assertTrue(report.slotResults.all { it.outcome != BiosGuidedFolderSetup.Outcome.CREATED })
        assertOldPreferences()
    }

    @Test fun genuinelyMissingFilesDoNotOfferAnIoRetry() {
        val dialog = applyReport(setupIncoming())
        assertEquals(View.GONE, dialog.getButton(AlertDialog.BUTTON_NEUTRAL).visibility)
        assertOldPreferences()
    }

    @Test fun readableUnrelatedFileIsIgnoredWithoutAnIoRetry() {
        provider.file("incoming/notes.txt", 37)
        val dialog = applyReport(setupIncoming())
        assertEquals(View.GONE, dialog.getButton(AlertDialog.BUTTON_NEUTRAL).visibility)
        assertFalse(message(dialog).contains("notes.txt"))
        assertOldPreferences()
    }

    @Test fun realVerifierPreservesValidOldPreferencesWhenAReportedCompleteCandidateBecomesInvalid() {
        createDs("incoming/DS")
        val report = setupIncoming()
        assertTrue(report.slotResults.filter { it.slot.consoleType == ConsoleType.DS }.all { it.outcome == BiosGuidedFolderSetup.Outcome.ALREADY_PRESENT })
        provider.file("incoming/DS/bios7.bin", 0)
        assertEquals(ConfigurationDirResult.Status.INVALID, verifier.checkConsoleConfigurationDirectory(ConsoleType.DS, report.dsDirectory!!.uri).status)

        applyReport(report)

        assertOldPreferences()
        assertEquals(ConfigurationDirResult.Status.VALID, verifier.checkDsConfigurationDirectory().status)
        assertEquals(ConfigurationDirResult.Status.VALID, verifier.checkDsiConfigurationDirectory().status)
    }

    @Test fun completeDsIsSelectedWhileMissingNandPreservesTheOldDsiPreference() {
        createDs("incoming/DS")
        createDsi("incoming/DSi", nand = false)
        val report = setupIncoming()

        applyReport(report)

        assertEquals(report.dsDirectory!!.uri, settings.getDsBiosDirectory())
        assertEquals(provider.treeUri("old-dsi"), settings.getDsiBiosDirectory())
        assertEquals(setOf(report.dsDirectory.uri.toString()), preferences.getStringSet("bios_dir", null))
        assertEquals(setOf(provider.treeUri("old-dsi").toString()), preferences.getStringSet("dsi_bios_dir", null))
    }

    @Test fun completeValidatedFoldersReplaceBothPreferences() {
        createDs("incoming/DS")
        createDsi("incoming/DSi", nand = true)
        val report = setupIncoming()

        applyReport(report)

        assertEquals(report.dsDirectory!!.uri, settings.getDsBiosDirectory())
        assertEquals(report.dsiDirectory!!.uri, settings.getDsiBiosDirectory())
        assertEquals(setOf(report.dsDirectory.uri.toString()), preferences.getStringSet("bios_dir", null))
        assertEquals(setOf(report.dsiDirectory.uri.toString()), preferences.getStringSet("dsi_bios_dir", null))
        assertEquals(ConfigurationDirResult.Status.VALID, verifier.checkDsiConfigurationDirectory().status)
    }

    @Test fun removingTheViewDuringSlowNandCopyDismissesItsProgressImmediately() {
        val copy = startSlowNandCopy()
        val oldView = fragment.viewLifecycleOwner
        try {
            controller.get().supportFragmentManager.beginTransaction().remove(fragment).commitNow()
            assertEquals(Lifecycle.State.DESTROYED, oldView.lifecycle.currentState)
            assertTrue(copy.operation.isCancelled)
            assertFalse("A destroyed screen must not retain its progress window while a provider read waits", copy.dialog.isShowing)
            assertOldPreferences()
        } finally {
            copy.releaseAndAwait()
            copy.assertOriginalsPreserved()
        }
    }

    @Test fun recreatingTheViewDuringSlowCopyRemovesOwnedPartialWhenTheReadReturns() {
        val copy = startSlowNandCopy()
        val oldView = fragment.viewLifecycleOwner
        try {
            controller.get().supportFragmentManager.beginTransaction().detach(fragment).commitNow()
            controller.get().supportFragmentManager.beginTransaction().attach(fragment).commitNow()
            assertEquals(Lifecycle.State.DESTROYED, oldView.lifecycle.currentState)
            assertTrue(copy.operation.isCancelled)
            copy.releaseAndAwait()

            assertFalse("Cancellation must remove the newly created partial NAND after the provider returns", copy.destination.exists())
            assertOldPreferences()
            copy.assertOriginalsPreserved()
        } finally {
            copy.releaseAndAwait()
        }
    }

    @Test fun aSecondScreenCannotSelectAnInFlightPartialNand() {
        val copy = startSlowNandCopy()
        var secondOperation: Job? = null
        try {
            controller.get().supportFragmentManager.beginTransaction().remove(fragment).commitNow()
            attachFirmwareFragment()
            val activeSecondOperation = startGuidedSetup()
            secondOperation = activeSecondOperation
            // The old read is deterministically paused after one written chunk. Pump real main
            // callbacks during this bounded negative assertion; no sleep acts as the oracle.
            val committedWhileBlocked = pumpMainUntil(1000) {
                settings.getDsiBiosDirectory() != provider.treeUri("old-dsi")
            }
            assertFalse("The second screen selected NAND while only ${copy.destination.length()} of ${copy.original.size} bytes existed", committedWhileBlocked)

            copy.releaseAndAwait()
            assertTrue("Second setup did not finish after the cancelled owner released the provider", pumpMainUntil(5000) { activeSecondOperation.isCompleted })
            assertEquals(provider.documentUri("incoming/DSi"), settings.getDsiBiosDirectory())
            assertArrayEquals(copy.original, provider.diskFile("incoming/DSi/nand.bin").readBytes())
            copy.assertOriginalsPreserved()
        } finally {
            copy.releaseAndAwait()
            secondOperation?.let { assertTrue("Second setup leaked", pumpMainUntil(5000) { it.isCompleted }) }
        }
    }

    @Test fun failedCancellationCleanupLeavesOnlyANoncanonicalPartialAndRetryCopiesTheFullNand() {
        val copy = startSlowNandCopy()
        try {
            provider.denyCleanup = true
            controller.get().supportFragmentManager.beginTransaction().remove(fragment).commitNow()
            copy.releaseAndAwait()
            assertEquals("Fixture must retain the incomplete copy after deletion refusal", 1024L, copy.destination.length())
            assertFalse("An undeletable partial must never have the bootable canonical name", provider.diskFile("incoming/DSi/nand.bin").exists())
            assertEquals(ConfigurationDirResult.Status.INVALID, verifier.checkConsoleConfigurationDirectory(ConsoleType.DSi, provider.treeUri("incoming/DSi")).status)
            assertOldPreferences()

            attachFirmwareFragment()
            val retry = startGuidedSetup()
            assertTrue("Retry did not finish", pumpMainUntil(5000) { retry.isCompleted })

            assertEquals(provider.documentUri("incoming/DSi"), settings.getDsiBiosDirectory())
            assertArrayEquals(copy.original, provider.diskFile("incoming/DSi/nand.bin").readBytes())
            assertEquals("The previous partial is pre-existing data on retry and must remain untouched", 1024L, copy.destination.length())
            copy.assertOriginalsPreserved()
        } finally {
            copy.releaseAndAwait()
        }
    }

    @Test fun aProviderThatRefusesRenameCannotPublishOrSelectTheCopiedNand() {
        createDs("incoming/DS")
        createDsi("incoming/DSi", nand = false)
        provider.file("incoming/nand.bin", 32768)
        val baseline = provider.snapshotFiles()
        provider.rejectRenames = true

        val report = setupIncoming()
        applyReport(report)

        assertEquals(BiosGuidedFolderSetup.Outcome.ERROR, report.nandResult.outcome)
        assertFalse(provider.diskFile("incoming/DSi/nand.bin").exists())
        assertEquals(provider.treeUri("old-dsi"), settings.getDsiBiosDirectory())
        assertEquals("Only the pre-existing BIOS files should remain after failed publication cleanup", 3, provider.diskFile("incoming/DSi").listFiles()!!.size)
        baseline.forEach { (path, bytes) -> assertArrayEquals(bytes, provider.diskFile(path).readBytes()) }
    }

    @Test fun aDestinationCreatedBeforePublicationIsNeverOverwrittenOrSelected() {
        createDs("incoming/DS")
        createDsi("incoming/DSi", nand = false)
        provider.file("incoming/nand.bin", 32768)
        val source = provider.diskFile("incoming/nand.bin").readBytes()
        val existing = ByteArray(128) { 0x6b }
        val opens = AtomicInteger()
        Shadows.shadowOf(context.contentResolver).registerInputStreamSupplier(provider.documentUri("incoming/nand.bin")) {
            if (opens.incrementAndGet() == 1) ByteArrayInputStream(source) else object : ByteArrayInputStream(source) {
                override fun close() {
                    super.close()
                    provider.diskFile("incoming/DSi/nand.bin").writeBytes(existing)
                }
            }
        }

        val report = setupIncoming()
        applyReport(report)

        assertEquals(BiosGuidedFolderSetup.Outcome.ERROR, report.nandResult.outcome)
        assertArrayEquals(existing, provider.diskFile("incoming/DSi/nand.bin").readBytes())
        assertEquals(provider.treeUri("old-dsi"), settings.getDsiBiosDirectory())
        assertArrayEquals(source, provider.diskFile("incoming/nand.bin").readBytes())
    }

    @Test fun completeCopyPublishesItsCanonicalNameOnlyAfterTheInputHasClosed() {
        createDs("incoming/DS")
        createDsi("incoming/DSi", nand = false)
        provider.file("incoming/nand.bin", 32768)
        val source = provider.diskFile("incoming/nand.bin").readBytes()
        val opens = AtomicInteger()
        var copyingInputClosed = false
        Shadows.shadowOf(context.contentResolver).registerInputStreamSupplier(provider.documentUri("incoming/nand.bin")) {
            if (opens.incrementAndGet() == 1) ByteArrayInputStream(source) else object : ByteArrayInputStream(source) {
                override fun close() { super.close(); copyingInputClosed = true }
            }
        }
        provider.beforeRename = { assertTrue("The copy must be closed before publication", copyingInputClosed) }

        val report = setupIncoming()
        applyReport(report)

        assertEquals(BiosGuidedFolderSetup.Outcome.CREATED, report.nandResult.outcome)
        assertEquals("Exactly one completed NAND should be published", 1, provider.renameCount)
        assertArrayEquals(source, provider.diskFile("incoming/DSi/nand.bin").readBytes())
        assertEquals(4, provider.diskFile("incoming/DSi").listFiles()!!.size)
        assertEquals(provider.documentUri("incoming/DSi"), settings.getDsiBiosDirectory())
    }

    private fun startSlowNandCopy(): SlowCopy {
        createDs("incoming/DS")
        createDsi("incoming/DSi", nand = false)
        provider.file("incoming/nand.bin", 32768)
        val original = provider.diskFile("incoming/nand.bin").readBytes()
        val baseline = provider.snapshotFiles()
        val gated = GatedCopyStream(original)
        val sourceOpens = AtomicInteger()
        // The first open checks that NAND is non-empty; only the actual copy is paused.
        Shadows.shadowOf(context.contentResolver).registerInputStreamSupplier(provider.documentUri("incoming/nand.bin")) {
            if (sourceOpens.incrementAndGet() == 2) gated else ByteArrayInputStream(original)
        }
        val operation = startGuidedSetup()
        val dialog = ShadowDialog.getLatestDialog() as AlertDialog
        try {
            assertTrue("Copy never reached its second read", gated.readEntered.await(5, TimeUnit.SECONDS))
            val destination = provider.diskFile(provider.createdDocumentIds.single().removePrefix("root/"))
            assertEquals(1024L, destination.length())
            assertOldPreferences()
            return SlowCopy(operation, dialog, gated, original, destination, baseline)
        } catch (error: Throwable) {
            gated.release.countDown()
            pumpMainUntil(5000) { operation.isCompleted }
            throw error
        }
    }

    private fun startGuidedSetup(): Job {
        val scopeJob = fragment.viewLifecycleOwner.lifecycleScope.coroutineContext[Job]!!
        val previousJobs = scopeJob.children.toSet()
        val method = CustomFirmwarePreferencesFragment::class.java.getDeclaredMethod(
            "runGuidedBiosSetup", Uri::class.java, BiosDirectoryPickerPreference::class.java, BiosDirectoryPickerPreference::class.java,
        ).apply { isAccessible = true }
        method.invoke(fragment, provider.treeUri("incoming"),
            fragment.findPreference<BiosDirectoryPickerPreference>("bios_dir")!!,
            fragment.findPreference<BiosDirectoryPickerPreference>("dsi_bios_dir")!!)
        val operation = scopeJob.children.single { it !in previousJobs }
        Shadows.shadowOf(Looper.getMainLooper()).idle()
        return operation
    }

    private fun pumpMainUntil(timeoutMillis: Long, condition: () -> Boolean): Boolean {
        val deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMillis)
        while (!condition() && System.nanoTime() < deadline) {
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            Thread.yield()
        }
        return condition()
    }

    private inner class SlowCopy(
        val operation: Job,
        val dialog: AlertDialog,
        private val stream: GatedCopyStream,
        val original: ByteArray,
        val destination: File,
        private val baseline: Map<String, ByteArray>,
    ) {
        fun releaseAndAwait() {
            stream.release.countDown()
            assertTrue("Setup owner did not finish after the provider read returned", pumpMainUntil(5000) { operation.isCompleted })
            assertTrue("Owned input stream was not closed", stream.closed.await(1, TimeUnit.SECONDS))
        }
        fun assertOriginalsPreserved() {
            baseline.forEach { (path, bytes) -> assertArrayEquals("Original changed: $path", bytes, provider.diskFile(path).readBytes()) }
        }
    }

    /** close() deliberately does not wake read(): these tests only promise cleanup after return. */
    private class GatedCopyStream(private val bytes: ByteArray) : InputStream() {
        val readEntered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val closed = CountDownLatch(1)
        private var position = 0
        override fun read(): Int {
            val byte = ByteArray(1)
            return if (read(byte, 0, 1) < 0) -1 else byte[0].toInt() and 0xff
        }
        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            if (position == 1024) {
                readEntered.countDown()
                if (!release.await(30, TimeUnit.SECONDS)) throw IOException("Test did not release gated copy")
            }
            if (position == bytes.size) return -1
            val count = minOf(length, if (position == 0) 1024 else bytes.size - position)
            bytes.copyInto(buffer, offset, position, position + count)
            position += count
            return count
        }
        override fun close() { closed.countDown() }
    }

    private fun setupIncoming(): BiosGuidedFolderSetup.Report = BiosGuidedFolderSetup(context).run(provider.treeUri("incoming"))!!

    private fun applyReport(report: BiosGuidedFolderSetup.Report): AlertDialog {
        val method = CustomFirmwarePreferencesFragment::class.java.getDeclaredMethod(
            "applyGuidedBiosSetupResult", BiosGuidedFolderSetup.Report::class.java, Uri::class.java,
            BiosDirectoryPickerPreference::class.java, BiosDirectoryPickerPreference::class.java,
        ).apply { isAccessible = true }
        method.invoke(fragment, report, provider.treeUri("incoming"),
            fragment.findPreference<BiosDirectoryPickerPreference>("bios_dir")!!,
            fragment.findPreference<BiosDirectoryPickerPreference>("dsi_bios_dir")!!)
        val dialog = ShadowDialog.getLatestDialog()
        assertNotNull(dialog)
        return dialog as AlertDialog
    }

    private fun message(dialog: AlertDialog) = dialog.findViewById<TextView>(android.R.id.message)!!.text.toString()

    private fun assertOldPreferences() {
        assertEquals(provider.treeUri("old-ds"), settings.getDsBiosDirectory())
        assertEquals(provider.treeUri("old-dsi"), settings.getDsiBiosDirectory())
        assertEquals(setOf(provider.treeUri("old-ds").toString()), preferences.getStringSet("bios_dir", null))
        assertEquals(setOf(provider.treeUri("old-dsi").toString()), preferences.getStringSet("dsi_bios_dir", null))
    }

    private fun createDs(path: String) {
        provider.file("$path/bios7.bin", 16384)
        provider.file("$path/bios9.bin", 4096)
        provider.file("$path/firmware.bin", 262144)
    }

    private fun createDsi(path: String, nand: Boolean) {
        provider.file("$path/bios7.bin", 65536)
        provider.file("$path/bios9.bin", 65536)
        provider.file("$path/firmware.bin", 131072, dsi = true)
        if (nand) provider.file("$path/nand.bin", 1024)
    }

    private inline fun <reified T> unused(): T = Proxy.newProxyInstance(T::class.java.classLoader, arrayOf(T::class.java)) { _, method, _ ->
        error("Unexpected dependency call: ${method.name}")
    } as T

    /** Robolectric's legacy query dispatch omits ContentResolver's Android O bundle bridge. */
    @Implements(ContentResolver::class)
    class DocumentsResolver : ShadowContentResolver() {
        @Implementation
        public override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor? {
            val provider = getProvider(uri)
            if (provider !is DocumentsProvider) return super.query(uri, projection, selection, selectionArgs, sortOrder)
            val queryArgs = Bundle().apply {
                putString(ContentResolver.QUERY_ARG_SQL_SELECTION, selection)
                putStringArray(ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS, selectionArgs)
                putString(ContentResolver.QUERY_ARG_SQL_SORT_ORDER, sortOrder)
            }
            return provider.query(uri, projection, queryArgs, null)
        }
    }

    private class BiosDocumentsProvider(private val root: File) : DocumentsProvider() {
        val unreadableIds = mutableSetOf<String>()
        val createdDocumentIds = ConcurrentLinkedQueue<String>()
        var denyCleanup = false
        var rejectRenames = false
        var renameCount = 0
        var beforeRename: () -> Unit = {}
        fun diskFile(path: String) = File(root, path)
        fun snapshotFiles() = root.walkTopDown().filter { it.isFile }.associate { it.relativeTo(root).invariantSeparatorsPath to it.readBytes() }
        fun directory(path: String) { File(root, path).mkdirs() }
        fun file(path: String, size: Int, dsi: Boolean = false) {
            val target = File(root, path)
            target.parentFile!!.mkdirs()
            val bytes = ByteArray(size)
            if (dsi) bytes[0x1D] = 0x57
            target.writeBytes(bytes)
        }
        fun treeUri(path: String) = DocumentsContract.buildTreeDocumentUri(AUTHORITY, "root/$path")
        fun documentUri(path: String) = DocumentsContract.buildDocumentUriUsingTree(treeUri("incoming"), "root/$path")
        private fun resolve(id: String) = if (id == "root") root else File(root, id.removePrefix("root/"))
        private fun id(file: File) = "root/" + file.relativeTo(root).invariantSeparatorsPath
        override fun onCreate() = true
        override fun isChildDocument(parentDocumentId: String, documentId: String) = documentId.startsWith("$parentDocumentId/")
        override fun queryRoots(projection: Array<out String>?) = MatrixCursor(projection ?: arrayOf(DocumentsContract.Root.COLUMN_ROOT_ID))
        override fun queryDocument(documentId: String, projection: Array<out String>?) = rows(listOf(resolve(documentId)), projection)
        override fun queryChildDocuments(parentDocumentId: String, projection: Array<out String>?, sortOrder: String?) = rows(resolve(parentDocumentId).listFiles().orEmpty().toList(), projection)
        private fun rows(files: List<File>, projection: Array<out String>?): MatrixCursor {
            val columns = projection ?: arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
            return MatrixCursor(columns).apply {
                files.filter { it.exists() }.forEach { file -> addRow(columns.map { column ->
                    when (column) {
                        DocumentsContract.Document.COLUMN_DOCUMENT_ID -> id(file)
                        DocumentsContract.Document.COLUMN_DISPLAY_NAME -> file.name
                        DocumentsContract.Document.COLUMN_MIME_TYPE -> if (file.isDirectory) DocumentsContract.Document.MIME_TYPE_DIR else "application/octet-stream"
                        DocumentsContract.Document.COLUMN_SIZE -> file.length()
                        DocumentsContract.Document.COLUMN_FLAGS -> DocumentsContract.Document.FLAG_SUPPORTS_WRITE or DocumentsContract.Document.FLAG_DIR_SUPPORTS_CREATE or DocumentsContract.Document.FLAG_SUPPORTS_DELETE
                        DocumentsContract.Document.COLUMN_LAST_MODIFIED -> file.lastModified()
                        else -> null
                    }
                }) }
            }
        }
        override fun openDocument(documentId: String, mode: String, signal: CancellationSignal?): ParcelFileDescriptor {
            if (documentId in unreadableIds) throw FileNotFoundException("Fixture: document remains listed but cannot be opened")
            return ParcelFileDescriptor.open(resolve(documentId), ParcelFileDescriptor.parseMode(mode))
        }
        override fun createDocument(parentDocumentId: String, mimeType: String, displayName: String): String {
            val file = File(resolve(parentDocumentId), displayName)
            if (file.exists()) throw FileNotFoundException("Fixture refuses to overwrite existing files")
            if (mimeType == DocumentsContract.Document.MIME_TYPE_DIR) check(file.mkdir()) else check(file.createNewFile())
            return id(file).also { createdDocumentIds += it }
        }
        override fun renameDocument(documentId: String, displayName: String): String {
            beforeRename()
            if (rejectRenames) throw FileNotFoundException("Fixture refuses rename")
            val original = resolve(documentId)
            val target = File(original.parentFile, displayName)
            if (target.exists()) throw FileNotFoundException("Fixture refuses to overwrite an existing destination")
            check(original.renameTo(target))
            renameCount++
            return id(target)
        }
        override fun deleteDocument(documentId: String) {
            if (denyCleanup) throw SecurityException("Fixture refuses partial cleanup")
            check(resolve(documentId).delete())
        }
    }

    companion object { private const val AUTHORITY = "me.magnum.melonds.bios-test" }
}
