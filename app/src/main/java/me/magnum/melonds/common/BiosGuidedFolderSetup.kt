package me.magnum.melonds.common

import android.content.Context
import android.content.res.AssetFileDescriptor
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import me.magnum.melonds.domain.model.BiosFileClassification
import me.magnum.melonds.domain.model.BiosSlot
import me.magnum.melonds.domain.model.ConsoleType
import me.magnum.melonds.domain.model.FirmwareConsoleType
import me.magnum.melonds.domain.services.BiosFileClassifier
import java.io.FileNotFoundException
import java.io.IOException
import java.util.UUID

/**
 * Turns a single folder full of loosely-named BIOS/firmware files (as they are commonly shared:
 * mixed together, named after whatever the source used) into the two folders melonDS actually
 * expects, with the files it recognises copied into the right one under the right name.
 *
 * What it does, given one folder the user already granted access to:
 * 1. Creates "DS" and "DSi" sub-folders in it if they are not already there.
 * 2. Looks at every loose file directly inside it (not inside DS/DSi themselves) and classifies
 *    it with [BiosFileClassifier], using size and, for firmware, the header byte at offset 0x1D.
 * 3. Copies every file it can place unambiguously into the matching sub-folder under its
 *    canonical name (bios7.bin, bios9.bin, firmware.bin). The DSi ARM7/ARM9 BIOS pair (both
 *    64 KB, no content signature) is resolved by elimination when only one of the two is still
 *    missing, and otherwise by a same-console filename hint ("7"/"arm7" vs "9"/"arm9") -- both
 *    documented per-outcome, since neither is a content-based decision.
 * 4. A file named exactly "nand.bin" (case-insensitively) is copied across as-is: NAND storage
 *    has no documented size or header signature, so it can only ever be recognised by name.
 *
 * Nothing already present at a destination is ever overwritten or deleted, and no source file is
 * ever deleted or moved: only copies are made, so a mistake here never loses the user's files.
 */
class BiosGuidedFolderSetup private constructor(
    private val context: Context,
    private val checkCancellation: () -> Unit,
) {
    constructor(context: Context) : this(context, {})

    /**
     * Serialize guided UI runs across recreated screens. A cancelled run retains ownership until
     * provider IO returns and its cleanup finishes; waiting callers do not inspect its partial files.
     * Cancellation is cooperative between IO calls, not a promise to interrupt a blocked provider.
     */
    suspend fun runCancellable(parentDirectoryUri: Uri): Report? = operationMutex.withLock {
        withContext(Dispatchers.IO) {
            val copyContext = coroutineContext
            BiosGuidedFolderSetup(context) { copyContext.ensureActive() }.run(parentDirectoryUri)
        }
    }


    enum class Outcome {
        /** A file was found and copied into place under its canonical name. */
        CREATED,
        /** The destination already had a usable file; it was left untouched. */
        ALREADY_PRESENT,
        /** No file matching this slot was found among the loose files. */
        MISSING,
        /** More than one candidate file could fill this slot and none could be ruled out. */
        AMBIGUOUS,
        /** An existing file has the wrong size/type; it was left untouched. */
        INVALID,
        /** A file could not be read, created or copied. */
        ERROR,
        /** Copying failed and the newly created partial file could not be removed. */
        CLEANUP_FAILED,
    }

    data class SlotResult(
        val slot: BiosSlot,
        val outcome: Outcome,
        /** Name(s) of the loose source file(s) involved, including unsuccessful copies. */
        val sourceFileNames: List<String> = emptyList(),
        /** True when a DSi ARM7/ARM9 assignment was made from the file name, not from content. */
        val resolvedByFileNameHint: Boolean = false,
        /** Actual name of an owned temporary file that the provider refused to remove. */
        val cleanupFileName: String? = null,
    )

    data class NandResult(val outcome: Outcome, val sourceFileName: String? = null, val cleanupFileName: String? = null)

    private data class CopyResult(val outcome: Outcome, val cleanupFileName: String? = null)

    data class Report(
        val dsDirectory: DocumentFile?,
        val dsiDirectory: DocumentFile?,
        val slotResults: List<SlotResult>,
        val nandResult: NandResult,
        /** Files whose size or firmware header could not be inspected; no slot was assigned. */
        val unreadableSourceNames: List<String> = emptyList(),
    )

    /**
     * @param parentDirectoryUri a tree URI the app already has read/write access to (e.g. just
     * returned by the system directory picker). Must point at an existing directory.
     * @return null if [parentDirectoryUri] does not resolve to a directory.
     */
    fun run(parentDirectoryUri: Uri): Report? {
        checkCancellation()
        val parentDir = DocumentFile.fromTreeUri(context, parentDirectoryUri)?.takeIf { it.isDirectory } ?: return null
        return run(parentDir)
    }

    /**
     * Same as [run] above, taking an already-resolved directory. Split out mainly so tests can
     * drive the actual logic with a plain [DocumentFile.fromFile] directory, sidestepping
     * [DocumentFile.fromTreeUri]'s dependency on a real Storage Access Framework tree Uri.
     */
    fun run(parentDir: DocumentFile): Report {
        checkCancellation()
        val dsDir = findChildIgnoringCase(parentDir, DS_FOLDER_NAME)?.takeIf { it.isDirectory } ?: parentDir.createDirectory(DS_FOLDER_NAME)
        val dsiDir = findChildIgnoringCase(parentDir, DSI_FOLDER_NAME)?.takeIf { it.isDirectory } ?: parentDir.createDirectory(DSI_FOLDER_NAME)

        val looseFiles = parentDir.listFiles().filter { it.isFile }

        val unreadableSourceNames = mutableListOf<String>()
        val unambiguousMatches = mutableMapOf<BiosSlot, MutableList<DocumentFile>>()
        val dsiBios7Or9Candidates = mutableListOf<DocumentFile>()
        var nandCandidate: DocumentFile? = null

        for (file in looseFiles) {
            checkCancellation()
            if (file.name?.equals(NAND_FILE_NAME, ignoreCase = true) == true) {
                nandCandidate = file
                continue
            }

            val classification = classifySource(file)
            if (classification == null) {
                unreadableSourceNames += file.name ?: file.uri.toString()
                continue
            }

            when (classification) {
                is BiosFileClassification.Unambiguous -> unambiguousMatches.getOrPut(classification.slot) { mutableListOf() }.add(file)
                is BiosFileClassification.Ambiguous -> dsiBios7Or9Candidates.add(file)
                BiosFileClassification.Unrecognized -> Unit
            }
        }

        val slotResults = mutableListOf<SlotResult>()
        for (slot in listOf(BiosSlot.DS_BIOS7, BiosSlot.DS_BIOS9, BiosSlot.DS_FIRMWARE, BiosSlot.DSI_FIRMWARE)) {
            checkCancellation()
            val destDir = if (slot.consoleType == ConsoleType.DS) dsDir else dsiDir
            slotResults += resolveSingleCandidateSlot(destDir, slot, unambiguousMatches[slot].orEmpty())
        }
        slotResults += resolveDsiBiosPair(dsiDir, dsiBios7Or9Candidates)

        checkCancellation()
        val nandResult = resolveNand(dsiDir, nandCandidate)
        checkCancellation()

        return Report(dsDir, dsiDir, slotResults, nandResult, unreadableSourceNames)
    }

    /** Null means inspection was unavailable, distinct from a readable unrecognized file. */
    private fun classifySource(file: DocumentFile): BiosFileClassification? {
        return try {
            checkCancellation()
            val size = getFileSize(file) ?: return null
            val header = if (size in FIRMWARE_SIZES) {
                readFirmwareConsoleTypeByte(file) ?: return null
            } else null
            checkCancellation()
            BiosFileClassifier.classify(size, header)
        } catch (e: IOException) {
            null
        } catch (e: SecurityException) {
            null
        } catch (e: IllegalArgumentException) {
            null
        }
    }

    private fun resolveSingleCandidateSlot(destDir: DocumentFile?, slot: BiosSlot, candidates: List<DocumentFile>): SlotResult {
        if (destDir == null) {
            return SlotResult(slot, Outcome.ERROR)
        }
        destDir.findFile(slot.canonicalFileName)?.let {
            return SlotResult(slot, validateExistingSlot(it, slot))
        }
        return when (candidates.size) {
            0 -> SlotResult(slot, Outcome.MISSING)
            1 -> {
                val source = candidates[0]
                copySlot(source, destDir, slot)
            }
            else -> SlotResult(slot, Outcome.AMBIGUOUS, candidates.mapNotNull { it.name })
        }
    }

    private fun resolveDsiBiosPair(dsiDir: DocumentFile?, candidates: List<DocumentFile>): List<SlotResult> {
        val bios7 = BiosSlot.DSI_BIOS7
        val bios9 = BiosSlot.DSI_BIOS9

        if (dsiDir == null) {
            return listOf(SlotResult(bios7, Outcome.ERROR), SlotResult(bios9, Outcome.ERROR))
        }

        val existingResults = listOf(bios7, bios9).mapNotNull { slot ->
            dsiDir.findFile(slot.canonicalFileName)?.let { SlotResult(slot, validateExistingSlot(it, slot)) }
        }
        // Invalid existing files still occupy their names: never overwrite them.
        val missingSlots = listOf(bios7, bios9).filter { slot -> existingResults.none { it.slot == slot } }

        if (missingSlots.isEmpty()) return existingResults
        if (candidates.isEmpty()) {
            return missingSlots.map { SlotResult(it, Outcome.MISSING) } + existingResults
        }

        // Exactly one 64 KB candidate and exactly one missing slot: no other file could fill it.
        if (missingSlots.size == 1 && candidates.size == 1) {
            val slot = missingSlots[0]
            val source = candidates[0]
            return listOf(copySlot(source, dsiDir, slot)) + existingResults
        }

        // Try a same-console filename hint ("...7..."/"arm7" vs "...9.../arm9") for whatever
        // remains unresolved. This is explicitly a name-based fallback, not a content decision.
        val remainingCandidates = candidates.toMutableList()
        val results = mutableListOf<SlotResult>()
        for (slot in missingSlots) {
            val hintDigit = if (slot == bios7) "7" else "9"
            val matchingHint = remainingCandidates.filter { matchesNameHint(it, hintDigit) }
            if (matchingHint.size == 1) {
                val hinted = matchingHint[0]
                remainingCandidates.remove(hinted)
                results += copySlot(hinted, dsiDir, slot, resolvedByFileNameHint = true)
            } else {
                results += SlotResult(slot, Outcome.AMBIGUOUS, candidates.mapNotNull { it.name })
            }
        }
        results += existingResults
        return results
    }

    private fun copySlot(source: DocumentFile, destDir: DocumentFile, slot: BiosSlot, resolvedByFileNameHint: Boolean = false): SlotResult {
        val copy = copyInto(source, destDir, slot.canonicalFileName)
        return SlotResult(slot, copy.outcome, listOf(source.name.orEmpty()), resolvedByFileNameHint, copy.cleanupFileName)
    }

    /** True when [file]'s name contains a same-console hint for [hintDigit] ("7" or "9"). */
    private fun matchesNameHint(file: DocumentFile, hintDigit: String): Boolean {
        val name = file.name?.lowercase().orEmpty()
        return name.contains("arm$hintDigit") || Regex("(^|[^0-9])$hintDigit([^0-9]|$)").containsMatchIn(name)
    }

    /** Case-insensitive [DocumentFile.findFile], since providers vary in whether they normalise case. */
    private fun findChildIgnoringCase(parent: DocumentFile, name: String): DocumentFile? {
        return parent.listFiles().firstOrNull { it.name?.equals(name, ignoreCase = true) == true }
    }

    private fun validateExistingSlot(file: DocumentFile, slot: BiosSlot): Outcome {
        return try {
            if (!file.isFile) return Outcome.INVALID
            val size = getFileSize(file) ?: return Outcome.ERROR
            if (size !in slot.expectedSizeBytes) return Outcome.INVALID
            if (slot == BiosSlot.DS_FIRMWARE || slot == BiosSlot.DSI_FIRMWARE) {
                val header = readFirmwareConsoleTypeByte(file) ?: return Outcome.ERROR
                val classification = BiosFileClassifier.classify(size, header)
                if (classification !is BiosFileClassification.Unambiguous || classification.slot != slot) {
                    return Outcome.INVALID
                }
            }
            Outcome.ALREADY_PRESENT
        } catch (e: IOException) {
            Outcome.ERROR
        } catch (e: SecurityException) {
            Outcome.ERROR
        } catch (e: IllegalArgumentException) {
            Outcome.ERROR
        }
    }

    private fun resolveNand(dsiDir: DocumentFile?, candidate: DocumentFile?): NandResult {
        if (dsiDir == null) return NandResult(Outcome.ERROR)
        dsiDir.findFile(NAND_FILE_NAME)?.let { return NandResult(validateNand(it)) }
        if (candidate == null) return NandResult(Outcome.MISSING)
        val candidateStatus = validateNand(candidate)
        if (candidateStatus != Outcome.ALREADY_PRESENT) return NandResult(candidateStatus, candidate.name)
        val copy = copyInto(candidate, dsiDir, NAND_FILE_NAME)
        return NandResult(copy.outcome, candidate.name, copy.cleanupFileName)
    }

    /** This only checks accessibility and non-empty storage, not the authenticity of a NAND dump. */
    private fun validateNand(file: DocumentFile): Outcome {
        return try {
            checkCancellation()
            if (!file.isFile) return Outcome.INVALID
            context.contentResolver.openInputStream(file.uri)?.use {
                checkCancellation()
                val firstByte = it.read()
                checkCancellation()
                if (firstByte == -1) Outcome.INVALID else Outcome.ALREADY_PRESENT
            } ?: Outcome.ERROR
        } catch (e: IOException) {
            Outcome.ERROR
        } catch (e: SecurityException) {
            Outcome.ERROR
        } catch (e: IllegalArgumentException) {
            Outcome.ERROR
        }
    }

    /**
     * Copy under a unique temporary name and publish only after both streams close. An undeletable
     * partial therefore cannot be mistaken for a canonical BIOS/NAND file on a later run.
     * Only this operation's new document is ever cleaned up. SAF has no universal atomic
     * rename-if-absent; check again immediately before rename and never fall back to direct copying.
     * Cancellation is observed between provider calls; a blocked read must return before cleanup.
     */
    private fun copyInto(source: DocumentFile, destDir: DocumentFile, destName: String): CopyResult {
        var dest: DocumentFile? = null
        var outcome = Outcome.ERROR
        var temporaryName = "$destName.melonds-${UUID.randomUUID()}.part"
        var cleanupFileName: String? = null
        try {
            checkCancellation()
            if (findChildIgnoringCase(destDir, destName) != null) throw IOException("Destination already exists")
            while (findChildIgnoringCase(destDir, temporaryName) != null) {
                checkCancellation()
                temporaryName = "$destName.melonds-${UUID.randomUUID()}.part"
            }
            val createdFile = destDir.createFile("application/octet-stream", temporaryName) ?: return CopyResult(Outcome.ERROR)
            dest = createdFile
            checkCancellation()
            val input = context.contentResolver.openInputStream(source.uri) ?: throw IOException("No input stream")
            input.use { inStream ->
                checkCancellation()
                val output = context.contentResolver.openOutputStream(createdFile.uri) ?: throw IOException("No output stream")
                output.use { outStream ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    while (true) {
                        checkCancellation()
                        val count = inStream.read(buffer)
                        checkCancellation()
                        if (count < 0) break
                        outStream.write(buffer, 0, count)
                    }
                }
            }
            checkCancellation()
            if (findChildIgnoringCase(destDir, destName) != null) throw IOException("Destination appeared during copying")
            if (!createdFile.renameTo(destName)) throw IOException("Provider could not publish the completed copy")
            if (createdFile.name != destName) throw IOException("Provider returned a different destination name")
            checkCancellation()
            outcome = Outcome.CREATED
        } catch (e: IOException) {
            // Cancellation still takes precedence if the provider failed while the view closed.
            checkCancellation()
        } catch (e: SecurityException) {
            checkCancellation()
        } catch (e: IllegalArgumentException) {
            checkCancellation()
        } finally {
            if (outcome != Outcome.CREATED && dest != null) {
                val deleted = try {
                    dest.delete()
                } catch (e: SecurityException) {
                    false
                } catch (e: IllegalArgumentException) {
                    false
                }
                if (!deleted) {
                    outcome = Outcome.CLEANUP_FAILED
                    cleanupFileName = dest.name ?: temporaryName
                }
            }
        }
        return CopyResult(outcome, cleanupFileName)
    }

    private fun getFileSize(file: DocumentFile): Long? {
        return try {
            context.contentResolver.openAssetFileDescriptor(file.uri, "r")?.use {
                if (it.length == AssetFileDescriptor.UNKNOWN_LENGTH) null else it.length
            }
        } catch (e: FileNotFoundException) {
            null
        }
    }

    /**
     * Reads the byte at [FirmwareConsoleType.HEADER_OFFSET]. Returns null if it can't be read, in
     * which case the caller must not treat the file as matching either console.
     */
    private fun readFirmwareConsoleTypeByte(file: DocumentFile): Int? {
        return try {
            context.contentResolver.openInputStream(file.uri)?.use { stream ->
                var skipped = 0L
                while (skipped < FirmwareConsoleType.HEADER_OFFSET) {
                    val n = stream.skip(FirmwareConsoleType.HEADER_OFFSET - skipped)
                    if (n <= 0) return null
                    skipped += n
                }
                val value = stream.read()
                if (value == -1) null else value
            }
        } catch (e: IOException) {
            null
        }
    }

    companion object {
        private val operationMutex = Mutex()

        const val DS_FOLDER_NAME = "DS"
        const val DSI_FOLDER_NAME = "DSi"
        const val NAND_FILE_NAME = "nand.bin"

        private val FIRMWARE_SIZES = (BiosSlot.DS_FIRMWARE.expectedSizeBytes.toList() + BiosSlot.DSI_FIRMWARE.expectedSizeBytes.toList()).distinct()
    }
}
