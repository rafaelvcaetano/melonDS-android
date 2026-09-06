package me.magnum.melonds.impl.emulator.recovery

enum class RecoveryProcessExitReason {
    ANR,
    CRASH,
    NATIVE_CRASH,
    EXCESSIVE_RESOURCE_USAGE,
    SELF_EXIT,
    FREEZER,
    INITIALIZATION_FAILURE,
    LOW_MEMORY,
    PERMISSION_CHANGE,
    SIGNALED,
    USER_REQUESTED,
    USER_STOPPED,
    UNKNOWN,
}

internal data class RecoveryProcessExit(
    val timestamp: Long,
    val reason: RecoveryProcessExitReason,
    val rawReason: Int,
    val status: Int,
    val description: String,
)

sealed class RecoveryCause {
    data class EmulatorStopped(val reason: String) : RecoveryCause()
    data class ProcessExit(
        val reason: RecoveryProcessExitReason,
        val rawReason: Int,
        val status: Int,
        val description: String,
    ) : RecoveryCause()
    data class ProcessRecreated(val detail: String) : RecoveryCause()
    data class RestoreFailed(val detail: String) : RecoveryCause()
}

data class RecoveryPrompt(
    val session: RecoverySession,
    val cause: RecoveryCause,
    val checkpointAvailable: Boolean,
    val automaticRestoreAllowed: Boolean,
)

internal fun classifyPreviousExit(
    session: RecoverySession,
    exits: List<RecoveryProcessExit>,
): RecoveryCause {
    val exit = exits.firstOrNull { it.timestamp >= session.startedAt }
        ?: return RecoveryCause.ProcessRecreated("no_matching_exit_record")
    return RecoveryCause.ProcessExit(
        reason = exit.reason,
        rawReason = exit.rawReason,
        status = exit.status,
        description = exit.description,
    )
}

internal fun canAutomaticallyRestore(
    session: RecoverySession,
    cause: RecoveryCause,
    checkpointAvailable: Boolean,
): Boolean {
    if (session.automaticRecoveryAttempted ||
        !session.sleeping ||
        !checkpointAvailable ||
        cause !is RecoveryCause.ProcessExit
    ) {
        return false
    }
    val sleepStartedAt = session.sleepStartedAt ?: return false
    val checkpointCreatedAt = session.checkpointCreatedAt ?: return false
    if (checkpointCreatedAt < sleepStartedAt) {
        return false
    }
    return when (cause.reason) {
        RecoveryProcessExitReason.LOW_MEMORY,
        RecoveryProcessExitReason.EXCESSIVE_RESOURCE_USAGE,
        RecoveryProcessExitReason.FREEZER -> true
        RecoveryProcessExitReason.SIGNALED -> cause.status == 9
        else -> false
    }
}

internal fun RecoverySession.startDeviceSleep(startedAt: Long): RecoverySession {
    return copy(sleeping = true, sleepStartedAt = startedAt)
}

internal fun RecoverySession.finishDeviceSleep(): RecoverySession {
    return copy(sleeping = false, sleepStartedAt = null)
}

internal fun RecoverySession.startAutomaticRecovery(sessionId: String): RecoverySession? {
    if (id != sessionId || automaticRecoveryAttempted) {
        return null
    }
    return copy(automaticRecoveryAttempted = true)
}

internal fun shouldDisableHardcoreForRecovery(
    recoveryPending: Boolean,
    recordedHardcore: Boolean,
    automaticRestore: Boolean,
): Boolean {
    return recoveryPending && (!automaticRestore || !recordedHardcore)
}
