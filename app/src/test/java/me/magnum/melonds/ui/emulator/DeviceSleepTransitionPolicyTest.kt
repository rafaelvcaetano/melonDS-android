package me.magnum.melonds.ui.emulator

import org.junit.Assert.assertEquals
import org.junit.Test

class DeviceSleepTransitionPolicyTest {

    @Test
    fun startsTransitionWhenRecoveryFinishesAfterScreenTurnsOff() {
        assertEquals(
            DeviceSleepTransitionAction.START,
            deviceSleepTransitionAction(
                screenOff = true,
                emulatorRunning = true,
                transitionActive = false,
            ),
        )
    }

    @Test
    fun ignoresTransitionUntilEmulatorIsRunning() {
        assertEquals(
            DeviceSleepTransitionAction.IGNORE,
            deviceSleepTransitionAction(
                screenOff = true,
                emulatorRunning = false,
                transitionActive = false,
            ),
        )
    }

    @Test
    fun ignoresTransitionWhileScreenIsOn() {
        assertEquals(
            DeviceSleepTransitionAction.IGNORE,
            deviceSleepTransitionAction(
                screenOff = false,
                emulatorRunning = true,
                transitionActive = false,
            ),
        )
    }

    @Test
    fun keepsExistingScreenOffTransitionActive() {
        assertEquals(
            DeviceSleepTransitionAction.KEEP_ACTIVE,
            deviceSleepTransitionAction(
                screenOff = true,
                emulatorRunning = true,
                transitionActive = true,
            ),
        )
    }
}
