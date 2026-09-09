package me.magnum.melonds.impl.emulator.recovery

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RecoveryPolicyTest {

    @Test
    fun automaticallyRestoresKnownNonUserExitsDuringSleep() {
        val eligibleReasons = listOf(
            RecoveryProcessExitReason.ANR,
            RecoveryProcessExitReason.CRASH,
            RecoveryProcessExitReason.NATIVE_CRASH,
            RecoveryProcessExitReason.LOW_MEMORY,
            RecoveryProcessExitReason.EXCESSIVE_RESOURCE_USAGE,
            RecoveryProcessExitReason.SELF_EXIT,
            RecoveryProcessExitReason.FREEZER,
            RecoveryProcessExitReason.INITIALIZATION_FAILURE,
            RecoveryProcessExitReason.PERMISSION_CHANGE,
            RecoveryProcessExitReason.SIGNALED,
        )

        eligibleReasons.forEach { reason ->
            assertTrue(canAutomaticallyRestore(session(), processExit(reason), true))
        }
    }

    @Test
    fun rejectsUserAndUnknownExits() {
        val ineligibleReasons = listOf(
            RecoveryProcessExitReason.USER_REQUESTED,
            RecoveryProcessExitReason.USER_STOPPED,
            RecoveryProcessExitReason.UNKNOWN,
        )

        ineligibleReasons.forEach { reason ->
            assertFalse(canAutomaticallyRestore(session(), processExit(reason), true))
        }
    }

    @Test
    fun discardsSessionsEndedByExplicitUserAction() {
        assertTrue(shouldDiscardRecovery(processExit(RecoveryProcessExitReason.USER_REQUESTED)))
        assertTrue(shouldDiscardRecovery(processExit(RecoveryProcessExitReason.USER_STOPPED)))
        assertFalse(shouldDiscardRecovery(processExit(RecoveryProcessExitReason.LOW_MEMORY)))
        assertFalse(
            shouldDiscardRecovery(
                RecoveryCause.ProcessRecreated("no_matching_exit_record"),
            ),
        )
    }

    @Test
    fun requiresActiveSleepWithCurrentCheckpoint() {
        val eligibleExit = processExit(RecoveryProcessExitReason.LOW_MEMORY)

        assertFalse(
            canAutomaticallyRestore(
                session = session(sleeping = false),
                cause = eligibleExit,
                checkpointAvailable = true,
            ),
        )
        assertFalse(
            canAutomaticallyRestore(
                session = session(),
                cause = eligibleExit,
                checkpointAvailable = false,
            ),
        )
        assertFalse(
            canAutomaticallyRestore(
                session = session(checkpointCreatedAt = 1L, sleepStartedAt = 2L),
                cause = eligibleExit,
                checkpointAvailable = true,
            ),
        )
        assertFalse(
            canAutomaticallyRestore(
                session = session(automaticRecoveryAttempted = true),
                cause = eligibleExit,
                checkpointAvailable = true,
            ),
        )
    }

    @Test
    fun selectsOnlyExitRecordsFromCurrentSession() {
        val currentExit = RecoveryProcessExit(
            timestamp = 20L,
            reason = RecoveryProcessExitReason.LOW_MEMORY,
            rawReason = 3,
            status = 0,
            description = "low memory",
        )
        val cause = classifyPreviousExit(
            session = session(startedAt = 10L),
            exits = listOf(currentExit),
        )

        assertEquals(
            RecoveryCause.ProcessExit(
                reason = RecoveryProcessExitReason.LOW_MEMORY,
                rawReason = 3,
                status = 0,
                description = "low memory",
            ),
            cause,
        )
        assertEquals(
            RecoveryCause.ProcessRecreated("no_matching_exit_record"),
            classifyPreviousExit(
                session = session(startedAt = 30L),
                exits = listOf(currentExit),
            ),
        )
    }

    @Test
    fun sleepTransitionsAreIdempotentAndBoundToSession() {
        val awakeSession = session(sleeping = false, sleepStartedAt = null)
        val sleepingSession = awakeSession.startDeviceSleep(20L)

        assertTrue(sleepingSession.sleeping)
        assertEquals(20L, sleepingSession.sleepStartedAt)
        assertFalse(sleepingSession.finishDeviceSleep().sleeping)
        assertNull(sleepingSession.finishDeviceSleep().sleepStartedAt)

        val recoveryStarted = sleepingSession.startAutomaticRecovery(sleepingSession.id)
        assertTrue(recoveryStarted?.automaticRecoveryAttempted == true)
        assertNull(recoveryStarted?.startAutomaticRecovery(sleepingSession.id))
        assertNull(sleepingSession.startAutomaticRecovery("different-session"))
    }

    @Test
    fun preservesHardcoreOnlyForAutomaticRecovery() {
        assertFalse(
            shouldDisableHardcoreForRecovery(
                recoveryPending = true,
                recordedHardcore = true,
                automaticRestore = true,
            )
        )
        assertTrue(
            shouldDisableHardcoreForRecovery(
                recoveryPending = true,
                recordedHardcore = true,
                automaticRestore = false,
            )
        )
        assertTrue(
            shouldDisableHardcoreForRecovery(
                recoveryPending = true,
                recordedHardcore = false,
                automaticRestore = true,
            )
        )
        assertFalse(
            shouldDisableHardcoreForRecovery(
                recoveryPending = false,
                recordedHardcore = false,
                automaticRestore = false,
            )
        )
    }

    private fun session(
        sleeping: Boolean = true,
        startedAt: Long = 1L,
        sleepStartedAt: Long? = 1L,
        checkpointCreatedAt: Long = 1L,
        automaticRecoveryAttempted: Boolean = false,
    ): RecoverySession {
        return RecoverySession(
            id = "session",
            processToken = "process",
            processId = 1,
            startedAt = startedAt,
            appVersionCode = 1L,
            type = RecoverySessionType.ROM,
            romUri = "content://rom",
            romName = "ROM",
            consoleType = null,
            hardcoreEnabled = true,
            active = true,
            sleeping = sleeping,
            sleepStartedAt = sleepStartedAt,
            checkpointFileName = "checkpoint-test.mln",
            checkpointSha256 = "checksum",
            checkpointCreatedAt = checkpointCreatedAt,
            stopReason = null,
            automaticRecoveryAttempted = automaticRecoveryAttempted,
        )
    }

    private fun processExit(
        reason: RecoveryProcessExitReason,
        status: Int = 0,
    ): RecoveryCause.ProcessExit {
        return RecoveryCause.ProcessExit(
            reason = reason,
            rawReason = -1,
            status = status,
            description = "",
        )
    }
}
