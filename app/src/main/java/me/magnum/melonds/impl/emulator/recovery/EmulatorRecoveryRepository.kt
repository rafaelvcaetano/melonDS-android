package me.magnum.melonds.impl.emulator.recovery

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Process
import android.os.SystemClock
import android.util.AtomicFile
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.magnum.melonds.domain.model.ConsoleType
import me.magnum.melonds.domain.model.emulator.EmulatorEvent
import me.magnum.melonds.domain.model.rom.Rom
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.security.MessageDigest
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class EmulatorRecoveryRepository(private val context: Context) {
    companion object {
        private const val TAG = "EmulatorRecovery"
        private const val RECOVERY_DIRECTORY = "emulator-recovery"
        private const val SESSION_FILE = "session.json"
        private const val JOURNAL_FILE = "journal.jsonl"
        private const val CHECKPOINT_FILE_PREFIX = "checkpoint-"
        private const val MAX_JOURNAL_BYTES = 512 * 1024L
    }

    private val lock = Any()
    private val processToken = UUID.randomUUID().toString()
    private val recoveryDirectory = File(context.filesDir, RECOVERY_DIRECTORY).apply { mkdirs() }
    private val sessionFile = AtomicFile(File(recoveryDirectory, SESSION_FILE))
    private val journalFile = File(recoveryDirectory, JOURNAL_FILE)
    private var pendingCheckpointFile: File? = null

    fun getPendingRecovery(): RecoveryPrompt? = synchronized(lock) {
        val session = readSession() ?: return@synchronized null
        if (!session.active) {
            return@synchronized null
        }

        val cause = when {
            session.stopReason != null -> RecoveryCause.EmulatorStopped(session.stopReason)
            session.processToken != processToken -> classifyPreviousProcessExit(session)
            else -> return@synchronized null
        }
        if (shouldDiscardRecovery(cause)) {
            markClean("android_user_exit")
            return@synchronized null
        }

        val checkpointAvailable = isCheckpointValid(session)
        RecoveryPrompt(
            session = session,
            cause = cause,
            checkpointAvailable = checkpointAvailable,
            automaticRestoreAllowed = canAutomaticallyRestore(
                session = session,
                cause = cause,
                checkpointAvailable = checkpointAvailable,
            ),
        )
    }

    fun beginRomSession(rom: Rom, hardcoreEnabled: Boolean) {
        val session = RecoverySession(
            id = UUID.randomUUID().toString(),
            processToken = processToken,
            processId = Process.myPid(),
            startedAt = System.currentTimeMillis(),
            appVersionCode = currentVersionCode(),
            type = RecoverySessionType.ROM,
            romUri = rom.uri.toString(),
            romName = rom.name,
            consoleType = null,
            hardcoreEnabled = hardcoreEnabled,
            active = true,
            sleeping = false,
            sleepStartedAt = null,
            checkpointFileName = null,
            checkpointSha256 = null,
            checkpointCreatedAt = null,
            stopReason = null,
            automaticRecoveryAttempted = false,
        )
        synchronized(lock) {
            deleteCheckpoints()
            if (writeSession(session)) {
                appendJournal("session_started", session.toJson())
            }
        }
    }

    fun beginFirmwareSession(consoleType: ConsoleType) {
        val session = RecoverySession(
            id = UUID.randomUUID().toString(),
            processToken = processToken,
            processId = Process.myPid(),
            startedAt = System.currentTimeMillis(),
            appVersionCode = currentVersionCode(),
            type = RecoverySessionType.FIRMWARE,
            romUri = null,
            romName = null,
            consoleType = consoleType.name,
            hardcoreEnabled = false,
            active = true,
            sleeping = false,
            sleepStartedAt = null,
            checkpointFileName = null,
            checkpointSha256 = null,
            checkpointCreatedAt = null,
            stopReason = null,
            automaticRecoveryAttempted = false,
        )
        synchronized(lock) {
            deleteCheckpoints()
            if (writeSession(session)) {
                appendJournal("session_started", session.toJson())
            }
        }
    }

    fun record(event: String, details: Map<String, Any?> = emptyMap()) {
        synchronized(lock) {
            appendJournal(event, JSONObject(details))
        }
    }

    fun markAutomaticRecoveryStarted(sessionId: String): Boolean = synchronized(lock) {
        val session = readSession() ?: return@synchronized false
        val updatedSession = session.startAutomaticRecovery(sessionId) ?: return@synchronized false
        if (!writeSession(updatedSession)) {
            return@synchronized false
        }
        appendJournal("automatic_recovery_started", JSONObject())
        true
    }

    fun markDeviceSleepStarted(): Boolean = synchronized(lock) {
        val session = readSession() ?: return@synchronized false
        val persisted = writeSession(session.startDeviceSleep(System.currentTimeMillis()))
        if (persisted) {
            appendJournal("device_sleep_started", JSONObject())
        }
        persisted
    }

    fun markDeviceSleepResumed(details: Map<String, Any?>) {
        synchronized(lock) {
            val session = readSession()
            val persisted = session == null || writeSession(session.finishDeviceSleep())
            appendJournal("device_sleep_resumed", JSONObject(details).put("persisted", persisted))
        }
    }

    fun recordUnexpectedStop(reason: EmulatorEvent.Stop.Reason): RecoveryPrompt? = synchronized(lock) {
        recordUnexpectedTermination("emulator_stop_${reason.name}", RecoveryCause.EmulatorStopped(reason.name))
    }

    fun recordUnexpectedTermination(reason: String, cause: RecoveryCause): RecoveryPrompt? = synchronized(lock) {
        val session = readSession() ?: return@synchronized null
        val updatedSession = session.copy(stopReason = reason)
        val persisted = writeSession(updatedSession)
        appendJournal(
            "emulator_stopped",
            JSONObject()
                .put("reason", reason)
                .put("persisted", persisted),
        )
        val checkpointAvailable = isCheckpointValid(updatedSession)
        RecoveryPrompt(
            session = updatedSession,
            cause = cause,
            checkpointAvailable = checkpointAvailable,
            automaticRestoreAllowed = false,
        )
    }

    fun checkpointTempUri(): Uri = synchronized(lock) {
        pendingCheckpointFile?.delete()
        val tempFile = File(recoveryDirectory, "$CHECKPOINT_FILE_PREFIX${UUID.randomUUID()}.tmp")
        pendingCheckpointFile = tempFile
        Uri.fromFile(tempFile)
    }

    fun commitCheckpoint(): Boolean = synchronized(lock) {
        val session = readSession() ?: return@synchronized false
        val tempFile = pendingCheckpointFile
        if (tempFile == null || !tempFile.isFile || tempFile.length() == 0L) {
            appendJournal("checkpoint_failed", JSONObject().put("reason", "empty_temp_file"))
            return@synchronized false
        }
        return@synchronized try {
            RandomAccessFile(tempFile, "rw").use { it.fd.sync() }
            val committedFile = File(
                recoveryDirectory,
                "$CHECKPOINT_FILE_PREFIX${System.currentTimeMillis()}.mln",
            )
            if (!tempFile.renameTo(committedFile)) {
                appendJournal("checkpoint_failed", JSONObject().put("reason", "atomic_rename_failed"))
                false
            } else {
                val checksum = committedFile.sha256()
                val previousCheckpoint = session.checkpointFileName
                val updatedSession = session.copy(
                    checkpointFileName = committedFile.name,
                    checkpointSha256 = checksum,
                    checkpointCreatedAt = System.currentTimeMillis(),
                )
                if (!writeSession(updatedSession)) {
                    committedFile.delete()
                    pendingCheckpointFile = null
                    appendJournal("checkpoint_failed", JSONObject().put("reason", "session_write_failed"))
                    false
                } else {
                    previousCheckpoint?.let { File(recoveryDirectory, it).delete() }
                    pendingCheckpointFile = null
                    appendJournal(
                        "checkpoint_committed",
                        JSONObject()
                            .put("bytes", committedFile.length())
                            .put("sha256", checksum),
                    )
                    true
                }
            }
        } catch (exception: Exception) {
            Log.e(TAG, "Failed to commit recovery checkpoint", exception)
            appendJournal("checkpoint_failed", JSONObject().put("reason", exception.javaClass.simpleName))
            false
        }
    }

    fun checkpointUri(): Uri? = synchronized(lock) {
        val session = readSession() ?: return@synchronized null
        getCheckpointFile(session)?.takeIf { isCheckpointValid(session) }?.let(Uri::fromFile)
    }

    fun markClean(reason: String) {
        synchronized(lock) {
            val session = readSession()
            if (session != null) {
                writeSession(session.copy(active = false, stopReason = null))
            }
            appendJournal("session_closed", JSONObject().put("reason", reason))
            deleteCheckpoints()
        }
    }

    fun discardRecovery(reason: String) {
        markClean(reason)
    }

    suspend fun exportDiagnostics(destination: Uri): Boolean = withContext(Dispatchers.IO) {
        try {
            context.contentResolver.openOutputStream(destination, "wt")?.use { output ->
                ZipOutputStream(output).use { zip ->
                    addTextEntry(zip, "summary.json", buildDiagnosticSummary().toString(2))
                    synchronized(lock) {
                        if (journalFile.isFile) {
                            addFileEntry(zip, JOURNAL_FILE, journalFile)
                        }
                        val rawSession = readAtomicFile()
                        if (rawSession != null) {
                            addBytesEntry(zip, SESSION_FILE, rawSession)
                        }
                    }
                    addExitTrace(zip)
                }
            } ?: return@withContext false
            record("diagnostics_exported")
            true
        } catch (exception: Exception) {
            Log.e(TAG, "Failed to export recovery diagnostics", exception)
            record("diagnostics_export_failed", mapOf("error" to exception.javaClass.simpleName))
            false
        }
    }

    private fun classifyPreviousProcessExit(session: RecoverySession): RecoveryCause {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            return RecoveryCause.ProcessRecreated("exit_reason_unavailable")
        }

        val activityManager = context.getSystemService(ActivityManager::class.java)
            ?: return RecoveryCause.ProcessRecreated("activity_manager_unavailable")
        val exits = activityManager
            .getHistoricalProcessExitReasons(context.packageName, session.processId, 10)
            .map { it.toRecoveryProcessExit() }
        return classifyPreviousExit(session, exits)
    }

    private fun buildDiagnosticSummary(): JSONObject {
        val session = synchronized(lock) { readSession() }
        val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
        val summary = JSONObject()
            .put("schemaVersion", 2)
            .put("packageName", context.packageName)
            .put("versionName", packageInfo.versionName)
            .put("versionCode", packageInfo.longVersionCode)
            .put("deviceManufacturer", Build.MANUFACTURER)
            .put("deviceModel", Build.MODEL)
            .put("androidVersion", Build.VERSION.RELEASE)
            .put("sdkInt", Build.VERSION.SDK_INT)
            .put("processId", Process.myPid())
            .put("processToken", processToken)
            .put("exportedAt", System.currentTimeMillis())
        session?.let { summary.put("session", it.toJson()) }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val exits = context.getSystemService(ActivityManager::class.java)
                ?.getHistoricalProcessExitReasons(context.packageName, 0, 10)
                .orEmpty()
            val exitReasons = org.json.JSONArray()
            exits.forEach { exitReasons.put(it.toJson()) }
            summary.put("exitReasons", exitReasons)
        }
        return summary
    }

    private fun addExitTrace(zip: ZipOutputStream) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            return
        }
        val exit = context.getSystemService(ActivityManager::class.java)
            ?.getHistoricalProcessExitReasons(context.packageName, 0, 1)
            ?.firstOrNull()
            ?: return
        val trace = exit.traceInputStream ?: return
        zip.putNextEntry(ZipEntry("exit-trace.bin"))
        trace.use { it.copyTo(zip) }
        zip.closeEntry()
    }

    private fun appendJournal(event: String, details: JSONObject) {
        try {
            if (journalFile.length() > MAX_JOURNAL_BYTES) {
                val rotated = File(recoveryDirectory, "$JOURNAL_FILE.old")
                rotated.delete()
                journalFile.renameTo(rotated)
            }
            val entry = JSONObject()
                .put("timestamp", System.currentTimeMillis())
                .put("elapsedRealtime", SystemClock.elapsedRealtime())
                .put("processId", Process.myPid())
                .put("processToken", processToken)
                .put("event", event)
                .put("details", details)
            journalFile.appendText(entry.toString() + "\n")
            Log.i(TAG, event)
        } catch (exception: Exception) {
            Log.e(TAG, "Failed to append recovery journal", exception)
        }
    }

    private fun writeSession(session: RecoverySession): Boolean {
        var output: FileOutputStream? = null
        return try {
            output = sessionFile.startWrite()
            output.write(session.toJson().toString().toByteArray())
            sessionFile.finishWrite(output)
            true
        } catch (exception: Exception) {
            try {
                output?.let(sessionFile::failWrite)
            } catch (rollbackException: Exception) {
                exception.addSuppressed(rollbackException)
            }
            Log.e(TAG, "Failed to write recovery session", exception)
            false
        }
    }

    private fun readSession(): RecoverySession? {
        val bytes = readAtomicFile() ?: return null
        return try {
            RecoverySession.fromJson(JSONObject(String(bytes)))
        } catch (exception: Exception) {
            Log.e(TAG, "Failed to read recovery session", exception)
            null
        }
    }

    private fun readAtomicFile(): ByteArray? {
        return try {
            sessionFile.readFully()
        } catch (_: Exception) {
            null
        }
    }

    private fun isCheckpointValid(session: RecoverySession): Boolean {
        if (session.appVersionCode != currentVersionCode()) {
            return false
        }
        val expectedChecksum = session.checkpointSha256 ?: return false
        val checkpointFile = getCheckpointFile(session) ?: return false
        return checkpointFile.isFile &&
            checkpointFile.length() > 0L &&
            checkpointFile.sha256() == expectedChecksum
    }

    private fun getCheckpointFile(session: RecoverySession): File? {
        val fileName = session.checkpointFileName ?: return null
        if (!fileName.startsWith(CHECKPOINT_FILE_PREFIX) || fileName.contains(File.separatorChar)) {
            return null
        }
        return File(recoveryDirectory, fileName)
    }

    private fun deleteCheckpoints() {
        pendingCheckpointFile = null
        recoveryDirectory.listFiles()
            ?.filter { it.name.startsWith(CHECKPOINT_FILE_PREFIX) }
            ?.forEach(File::delete)
    }

    private fun currentVersionCode(): Long {
        return context.packageManager.getPackageInfo(context.packageName, 0).longVersionCode
    }

    private fun addTextEntry(zip: ZipOutputStream, name: String, text: String) {
        addBytesEntry(zip, name, text.toByteArray())
    }

    private fun addFileEntry(zip: ZipOutputStream, name: String, file: File) {
        zip.putNextEntry(ZipEntry(name))
        file.inputStream().use { it.copyTo(zip) }
        zip.closeEntry()
    }

    private fun addBytesEntry(zip: ZipOutputStream, name: String, bytes: ByteArray) {
        zip.putNextEntry(ZipEntry(name))
        zip.write(bytes)
        zip.closeEntry()
    }

    private fun File.sha256(): String {
        val digest = MessageDigest.getInstance("SHA-256")
        inputStream().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) {
                    break
                }
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun ApplicationExitInfo.toJson(): JSONObject {
        return JSONObject()
            .put("timestamp", timestamp)
            .put("reason", reason)
            .put("status", status)
            .put("importance", importance)
            .put("description", description.orEmpty())
            .put("processName", processName)
            .put("pss", pss)
            .put("rss", rss)
    }

    private fun ApplicationExitInfo.toRecoveryProcessExit(): RecoveryProcessExit {
        return RecoveryProcessExit(
            timestamp = timestamp,
            reason = when (reason) {
                ApplicationExitInfo.REASON_ANR -> RecoveryProcessExitReason.ANR
                ApplicationExitInfo.REASON_CRASH -> RecoveryProcessExitReason.CRASH
                ApplicationExitInfo.REASON_CRASH_NATIVE -> RecoveryProcessExitReason.NATIVE_CRASH
                ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE ->
                    RecoveryProcessExitReason.EXCESSIVE_RESOURCE_USAGE
                ApplicationExitInfo.REASON_EXIT_SELF -> RecoveryProcessExitReason.SELF_EXIT
                ApplicationExitInfo.REASON_FREEZER -> RecoveryProcessExitReason.FREEZER
                ApplicationExitInfo.REASON_INITIALIZATION_FAILURE ->
                    RecoveryProcessExitReason.INITIALIZATION_FAILURE
                ApplicationExitInfo.REASON_LOW_MEMORY -> RecoveryProcessExitReason.LOW_MEMORY
                ApplicationExitInfo.REASON_PERMISSION_CHANGE -> RecoveryProcessExitReason.PERMISSION_CHANGE
                ApplicationExitInfo.REASON_SIGNALED -> RecoveryProcessExitReason.SIGNALED
                ApplicationExitInfo.REASON_USER_REQUESTED -> RecoveryProcessExitReason.USER_REQUESTED
                ApplicationExitInfo.REASON_USER_STOPPED -> RecoveryProcessExitReason.USER_STOPPED
                else -> RecoveryProcessExitReason.UNKNOWN
            },
            rawReason = reason,
            status = status,
            description = description.orEmpty(),
        )
    }
}

enum class RecoverySessionType {
    ROM,
    FIRMWARE,
}

data class RecoverySession(
    val id: String,
    val processToken: String,
    val processId: Int,
    val startedAt: Long,
    val appVersionCode: Long,
    val type: RecoverySessionType,
    val romUri: String?,
    val romName: String?,
    val consoleType: String?,
    val hardcoreEnabled: Boolean,
    val active: Boolean,
    val sleeping: Boolean,
    val sleepStartedAt: Long?,
    val checkpointFileName: String?,
    val checkpointSha256: String?,
    val checkpointCreatedAt: Long?,
    val stopReason: String?,
    val automaticRecoveryAttempted: Boolean,
) {
    fun toJson(): JSONObject {
        return JSONObject()
            .put("schemaVersion", 3)
            .put("id", id)
            .put("processToken", processToken)
            .put("processId", processId)
            .put("startedAt", startedAt)
            .put("appVersionCode", appVersionCode)
            .put("type", type.name)
            .put("romUri", romUri)
            .put("romName", romName)
            .put("consoleType", consoleType)
            .put("hardcoreEnabled", hardcoreEnabled)
            .put("active", active)
            .put("sleeping", sleeping)
            .put("sleepStartedAt", sleepStartedAt)
            .put("checkpointFileName", checkpointFileName)
            .put("checkpointSha256", checkpointSha256)
            .put("checkpointCreatedAt", checkpointCreatedAt)
            .put("stopReason", stopReason)
            .put("automaticRecoveryAttempted", automaticRecoveryAttempted)
    }

    companion object {
        fun fromJson(json: JSONObject): RecoverySession {
            return RecoverySession(
                id = json.getString("id"),
                processToken = json.getString("processToken"),
                processId = json.getInt("processId"),
                startedAt = json.getLong("startedAt"),
                appVersionCode = json.getLong("appVersionCode"),
                type = RecoverySessionType.valueOf(json.getString("type")),
                romUri = json.optString("romUri").takeIf { it.isNotEmpty() && it != "null" },
                romName = json.optString("romName").takeIf { it.isNotEmpty() && it != "null" },
                consoleType = json.optString("consoleType").takeIf { it.isNotEmpty() && it != "null" },
                hardcoreEnabled = json.optBoolean("hardcoreEnabled"),
                active = json.optBoolean("active"),
                sleeping = json.optBoolean("sleeping"),
                sleepStartedAt = json.optLong("sleepStartedAt").takeIf { it > 0L },
                checkpointFileName = json.optString("checkpointFileName").takeIf { it.isNotEmpty() && it != "null" },
                checkpointSha256 = json.optString("checkpointSha256").takeIf { it.isNotEmpty() && it != "null" },
                checkpointCreatedAt = json.optLong("checkpointCreatedAt").takeIf { it > 0L },
                stopReason = json.optString("stopReason").takeIf { it.isNotEmpty() && it != "null" },
                automaticRecoveryAttempted = json.optBoolean("automaticRecoveryAttempted"),
            )
        }
    }
}
