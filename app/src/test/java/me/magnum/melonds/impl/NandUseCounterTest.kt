package me.magnum.melonds.impl

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NandUseCounterTest {
    @Test
    fun `close without an open never underflows or closes native state`() {
        val counter = NandUseCounter()

        assertFalse(counter.release())
        assertTrue(counter.acquire())
        assertTrue(counter.release())
        assertFalse(counter.release())
    }

    @Test
    fun `native state closes only after the last client releases it`() {
        val counter = NandUseCounter()

        assertTrue(counter.acquire())
        assertFalse(counter.acquire())
        assertFalse(counter.release())
        assertTrue(counter.release())
    }
}
