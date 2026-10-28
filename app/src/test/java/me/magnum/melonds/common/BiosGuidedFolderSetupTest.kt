package me.magnum.melonds.common

import android.app.Application
import android.content.ContentResolver
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import androidx.documentfile.provider.DeleteDeniedDocumentFile
import androidx.test.core.app.ApplicationProvider
import me.magnum.melonds.domain.model.BiosSlot
import me.magnum.melonds.domain.model.ConsoleType
import me.magnum.melonds.domain.model.FirmwareConsoleType
import me.magnum.melonds.ui.settings.fragments.CustomFirmwarePreferencesFragment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.shadows.ShadowContentResolver
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

/**
 * Exercises [BiosGuidedFolderSetup] against a real temp directory via [DocumentFile.fromFile],
 * which -- unlike [DocumentFile.fromTreeUri] -- needs no Storage Access Framework provider under
 * Robolectric (see [BiosGuidedFolderSetup.run] overload taking a [DocumentFile] directly).
 */
@RunWith(RobolectricTestRunner::class)
// Preserve the file-backed ContentResolver behavior used by these fixtures.
@Config(application = Application::class, sdk = [28])
class BiosGuidedFolderSetupTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val context by lazy { ApplicationProvider.getApplicationContext<Application>() }
    private val setup by lazy { BiosGuidedFolderSetup(context) }

    @Test
    fun `full DS and DSi setup from arbitrarily named loose files creates folders and copies files under their canonical names`() {
        writeFile("random_bios_a.bin", 16384) // DS bios7
        writeFile("random_bios_b.bin", 4096) // DS bios9
        writeFile("random_firmware.bin", 262144, offset = FirmwareConsoleType.HEADER_OFFSET, value = 0xFF) // DS firmware
        writeFile("dsi_arm7_hint.bin", 65536) // DSi bios7/9 pair, resolved by name hint below
        writeFile("dsi_arm9_hint.bin", 65536)
        writeFile("dsi_firmware.bin", 131072, offset = FirmwareConsoleType.HEADER_OFFSET, value = FirmwareConsoleType.DSI_HEADER_BYTE)
        writeFile("nand.bin", 1024)

        val report = setup.run(DocumentFile.fromFile(tempFolder.root))

        assertNotNull(report.dsDirectory)
        assertNotNull(report.dsiDirectory)
        assertEquals("DS", report.dsDirectory?.name)
        assertEquals("DSi", report.dsiDirectory?.name)

        assertEquals(16384L, File(tempFolder.root, "DS/bios7.bin").length())
        assertEquals(4096L, File(tempFolder.root, "DS/bios9.bin").length())
        assertEquals(262144L, File(tempFolder.root, "DS/firmware.bin").length())
        assertEquals(65536L, File(tempFolder.root, "DSi/bios7.bin").length())
        assertEquals(65536L, File(tempFolder.root, "DSi/bios9.bin").length())
        assertEquals(131072L, File(tempFolder.root, "DSi/firmware.bin").length())
        assertTrue(File(tempFolder.root, "DSi/nand.bin").exists())

        assertSlot(report, BiosSlot.DS_BIOS7, BiosGuidedFolderSetup.Outcome.CREATED, listOf("random_bios_a.bin"))
        assertSlot(report, BiosSlot.DS_BIOS9, BiosGuidedFolderSetup.Outcome.CREATED, listOf("random_bios_b.bin"))
        assertSlot(report, BiosSlot.DS_FIRMWARE, BiosGuidedFolderSetup.Outcome.CREATED, listOf("random_firmware.bin"))
        assertSlot(report, BiosSlot.DSI_FIRMWARE, BiosGuidedFolderSetup.Outcome.CREATED, listOf("dsi_firmware.bin"))

        val bios7Result = report.slotResults.first { it.slot == BiosSlot.DSI_BIOS7 }
        assertEquals(BiosGuidedFolderSetup.Outcome.CREATED, bios7Result.outcome)
        assertEquals(listOf("dsi_arm7_hint.bin"), bios7Result.sourceFileNames)
        assertTrue(bios7Result.resolvedByFileNameHint)

        val bios9Result = report.slotResults.first { it.slot == BiosSlot.DSI_BIOS9 }
        assertEquals(BiosGuidedFolderSetup.Outcome.CREATED, bios9Result.outcome)
        assertEquals(listOf("dsi_arm9_hint.bin"), bios9Result.sourceFileNames)
        assertTrue(bios9Result.resolvedByFileNameHint)

        assertEquals(BiosGuidedFolderSetup.Outcome.CREATED, report.nandResult.outcome)
        assertEquals("nand.bin", report.nandResult.sourceFileName)
    }

    @Test
    fun `a source file that fails mid-copy leaves no partial destination file behind`() {
        val source = writeFile("broken_bios7.bin", 16384)
        val sourceUri = DocumentFile.fromFile(source).uri

        // The size read (openAssetFileDescriptor, used for classification) goes straight to the
        // real file and is unaffected; only the later read during the actual copy is poisoned --
        // this is what exercises copyInto's cleanup of the partially created destination file.
        Shadows.shadowOf(context.contentResolver).registerInputStream(sourceUri, object : InputStream() {
            override fun read(): Int = throw IOException("simulated failure mid-copy")
            override fun read(b: ByteArray, off: Int, len: Int): Int = throw IOException("simulated failure mid-copy")
        })

        val report = setup.run(DocumentFile.fromFile(tempFolder.root))

        val result = report.slotResults.first { it.slot == BiosSlot.DS_BIOS7 }
        assertEquals("ERROR", result.outcome.name)
        assertEquals(listOf("broken_bios7.bin"), result.sourceFileNames)
        assertFalse("a 0-byte destination file must not be left behind", File(tempFolder.root, "DS/bios7.bin").exists())
    }

    @Test
    fun `two same-size DSi BIOS candidates both hinting at the same slot are AMBIGUOUS and nothing is copied`() {
        writeFile("dsi_bios_7_a.bin", 65536)
        writeFile("dsi_bios_7_b.bin", 65536)

        val report = setup.run(DocumentFile.fromFile(tempFolder.root))

        val bios7Result = report.slotResults.first { it.slot == BiosSlot.DSI_BIOS7 }
        assertEquals(BiosGuidedFolderSetup.Outcome.AMBIGUOUS, bios7Result.outcome)
        assertEquals(setOf("dsi_bios_7_a.bin", "dsi_bios_7_b.bin"), bios7Result.sourceFileNames.toSet())

        val bios9Result = report.slotResults.first { it.slot == BiosSlot.DSI_BIOS9 }
        assertEquals(BiosGuidedFolderSetup.Outcome.AMBIGUOUS, bios9Result.outcome)

        assertFalse(File(tempFolder.root, "DSi/bios7.bin").exists())
        assertFalse(File(tempFolder.root, "DSi/bios9.bin").exists())
    }

    @Test
    fun `an existing lowercase ds folder is reused instead of creating a second DS folder`() {
        tempFolder.newFolder("ds")
        writeFile("loose_bios7.bin", 16384)

        val report = setup.run(DocumentFile.fromFile(tempFolder.root))

        assertEquals("ds", report.dsDirectory?.name)
        val dsLikeFolders = tempFolder.root.listFiles { f -> f.isDirectory && f.name.equals("DS", ignoreCase = true) }.orEmpty()
        assertEquals(1, dsLikeFolders.size)
        assertTrue(File(tempFolder.root, "ds/bios7.bin").exists())
    }

    @Test
    fun `known limitation - a 256KB save file without the DSi header byte is classified as DS firmware regardless of its sav extension`() {
        // BiosFileClassifier only ever looks at size and (for firmware) the header byte at 0x1D;
        // the file's extension/name plays no part. A 256 KB .sav with no DSi signature therefore
        // matches DS_FIRMWARE exactly like a real firmware.bin of the same size would. This is a
        // documented, accepted limitation of content-only classification, not something this
        // change is meant to alter.
        writeFile("somebackup.sav", 262144)

        val report = setup.run(DocumentFile.fromFile(tempFolder.root))

        val result = report.slotResults.first { it.slot == BiosSlot.DS_FIRMWARE }
        assertEquals(BiosGuidedFolderSetup.Outcome.CREATED, result.outcome)
        assertEquals(listOf("somebackup.sav"), result.sourceFileNames)
        assertTrue(File(tempFolder.root, "DS/firmware.bin").exists())
    }

    @Test
    fun `an existing empty BIOS is invalid and is never overwritten`() {
        tempFolder.newFolder("DS")
        val existing = writeFile("DS/bios7.bin", 0)
        writeFile("valid_bios7.bin", 16384)

        val report = setup.run(DocumentFile.fromFile(tempFolder.root))

        assertEquals("INVALID", report.slotResults.first { it.slot == BiosSlot.DS_BIOS7 }.outcome.name)
        assertTrue(existing.exists())
        assertEquals(0L, existing.length())
        assertEquals(16384L, File(tempFolder.root, "valid_bios7.bin").length())
    }

    @Test
    fun `an existing DSi BIOS of the wrong size is invalid and is never overwritten`() {
        tempFolder.newFolder("DSi")
        val existing = writeFile("DSi/bios7.bin", 16384)
        writeFile("dsi_arm7.bin", 65536)
        writeFile("dsi_arm9.bin", 65536)

        val report = setup.run(DocumentFile.fromFile(tempFolder.root))

        assertEquals("INVALID", report.slotResults.first { it.slot == BiosSlot.DSI_BIOS7 }.outcome.name)
        assertEquals(16384L, existing.length())
    }

    @Test
    fun `an existing firmware for the wrong console is invalid and is never overwritten`() {
        tempFolder.newFolder("DS")
        val existing = writeFile("DS/firmware.bin", 131072, FirmwareConsoleType.HEADER_OFFSET, FirmwareConsoleType.DSI_HEADER_BYTE)
        val original = existing.readBytes()

        val report = setup.run(DocumentFile.fromFile(tempFolder.root))

        assertEquals("INVALID", report.slotResults.first { it.slot == BiosSlot.DS_FIRMWARE }.outcome.name)
        assertTrue(original.contentEquals(existing.readBytes()))
    }

    @Test
    fun `a DSi folder without NAND is not eligible to replace a configured directory`() {
        writeFile("dsi_arm7.bin", 65536)
        writeFile("dsi_arm9.bin", 65536)
        writeFile("dsi_firmware.bin", 131072, FirmwareConsoleType.HEADER_OFFSET, FirmwareConsoleType.DSI_HEADER_BYTE)
        val report = setup.run(DocumentFile.fromFile(tempFolder.root))

        val gate = CustomFirmwarePreferencesFragment::class.java.getDeclaredMethod(
            "allMandatorySlotsSatisfied", BiosGuidedFolderSetup.Report::class.java, ConsoleType::class.java,
        ).apply { isAccessible = true }

        assertFalse(gate.invoke(CustomFirmwarePreferencesFragment(), report, ConsoleType.DSi) as Boolean)
    }

    @Test
    @Config(shadows = [FailingOutputResolver::class])
    fun `a destination write failure is an error and preserves the source for retry`() {
        val source = writeFile("bios7.bin", 16384)
        val destination = File(tempFolder.root, "DS/bios7.bin")

        val report = setup.run(DocumentFile.fromFile(tempFolder.root))

        assertEquals("ERROR", report.slotResults.first { it.slot == BiosSlot.DS_BIOS7 }.outcome.name)
        assertFalse(destination.exists())
        assertEquals(16384L, source.length())
    }

    @Test
    @Config(shadows = [NullInputResolver::class])
    fun `a null input stream is an error and removes the new destination`() {
        writeFile("bios7.bin", 16384)

        val report = setup.run(DocumentFile.fromFile(tempFolder.root))

        assertEquals("ERROR", report.slotResults.first { it.slot == BiosSlot.DS_BIOS7 }.outcome.name)
        assertFalse(File(tempFolder.root, "DS/bios7.bin").exists())
    }

    @Test
    @Config(shadows = [NullOutputResolver::class])
    fun `a null output stream is an error and removes the new destination`() {
        writeFile("bios7.bin", 16384)

        val report = setup.run(DocumentFile.fromFile(tempFolder.root))

        assertEquals("ERROR", report.slotResults.first { it.slot == BiosSlot.DS_BIOS7 }.outcome.name)
        assertFalse(File(tempFolder.root, "DS/bios7.bin").exists())
    }

    @Test
    fun `permission revoked after classification is reported as an error`() {
        val source = writeFile("bios7.bin", 16384)
        Shadows.shadowOf(context.contentResolver).registerInputStreamSupplier(Uri.fromFile(source)) {
            throw SecurityException("permission revoked")
        }

        val report = setup.run(DocumentFile.fromFile(tempFolder.root))

        assertEquals("ERROR", report.slotResults.first { it.slot == BiosSlot.DS_BIOS7 }.outcome.name)
        assertFalse(File(tempFolder.root, "DS/bios7.bin").exists())
        assertTrue(source.exists())
    }

    @Test
    fun `failed cleanup of a partial copy is reported separately`() {
        val source = writeFile("bios7.bin", 16384)
        Shadows.shadowOf(context.contentResolver).registerInputStreamSupplier(Uri.fromFile(source)) {
            throw IOException("source unavailable")
        }

        val report = setup.run(DeleteDeniedDocumentFile(DocumentFile.fromFile(tempFolder.root)))

        assertEquals("CLEANUP_FAILED", report.slotResults.first { it.slot == BiosSlot.DS_BIOS7 }.outcome.name)
        assertFalse("An incomplete copy must not occupy the canonical BIOS name", File(tempFolder.root, "DS/bios7.bin").exists())
        assertEquals("Only the owned partial should remain", 1, File(tempFolder.root, "DS").listFiles()!!.size)
        assertEquals(16384L, source.length())
    }

    @Test
    fun `empty existing NAND is invalid and preserved`() {
        tempFolder.newFolder("DSi")
        val nand = writeFile("DSi/nand.bin", 0)

        val report = setup.run(DocumentFile.fromFile(tempFolder.root))

        assertEquals("INVALID", report.nandResult.outcome.name)
        assertTrue(nand.exists())
        assertEquals(0L, nand.length())
    }

    @Implements(ContentResolver::class)
    class NullInputResolver : ShadowContentResolver() {
        @Implementation
        public override fun openInputStream(uri: Uri): InputStream? = null
    }

    @Implements(ContentResolver::class)
    class FailingOutputResolver : ShadowContentResolver() {
        @Implementation
        public override fun openOutputStream(uri: Uri): OutputStream = object : OutputStream() {
            override fun write(b: Int) = throw IOException("storage full")
        }
    }

    @Implements(ContentResolver::class)
    class NullOutputResolver : ShadowContentResolver() {
        @Implementation
        public override fun openOutputStream(uri: Uri): OutputStream? = null
    }

    private fun assertSlot(
        report: BiosGuidedFolderSetup.Report,
        slot: BiosSlot,
        outcome: BiosGuidedFolderSetup.Outcome,
        sourceFileNames: List<String>,
    ) {
        val result = report.slotResults.first { it.slot == slot }
        assertEquals(outcome, result.outcome)
        assertEquals(sourceFileNames, result.sourceFileNames)
        assertEquals(slot.consoleType, if (slot == BiosSlot.DS_BIOS7 || slot == BiosSlot.DS_BIOS9 || slot == BiosSlot.DS_FIRMWARE) ConsoleType.DS else ConsoleType.DSi)
    }

    private fun writeFile(name: String, size: Int, offset: Int? = null, value: Int? = null): File {
        val bytes = ByteArray(size)
        if (offset != null && value != null) {
            bytes[offset] = value.toByte()
        }
        val file = File(tempFolder.root, name)
        file.writeBytes(bytes)
        return file
    }
}
