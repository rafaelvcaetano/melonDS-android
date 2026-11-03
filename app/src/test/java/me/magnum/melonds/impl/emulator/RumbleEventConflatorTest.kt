package me.magnum.melonds.impl.emulator

import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import me.magnum.melonds.domain.model.emulator.EmulatorEvent
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RumbleEventConflatorTest {
    @Test
    fun `identical start pulses are not deduplicated`() = runTest {
        val conflator = RumbleEventConflator()
        val observed = mutableListOf<EmulatorEvent>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            conflator.events.take(2).toList(observed)
        }

        conflator.emit(EmulatorEvent.RumbleStart(16))
        conflator.emit(EmulatorEvent.RumbleStart(16))
        runCurrent()

        assertEquals(
            listOf(EmulatorEvent.RumbleStart(16), EmulatorEvent.RumbleStart(16)),
            observed,
        )
    }

    @Test
    fun `stalled consumer retains only the latest rumble state`() = runTest {
        val conflator = RumbleEventConflator()

        repeat(10_000) {
            conflator.emit(EmulatorEvent.RumbleStart(16))
            conflator.emit(EmulatorEvent.RumbleStop)
        }
        conflator.emit(EmulatorEvent.RumbleStart(32))

        assertEquals(EmulatorEvent.RumbleStart(32), conflator.events.first())
    }
}
