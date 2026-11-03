package me.magnum.melonds.common.romprocessors

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.IOException

class ArchiveReadBudgetTest {
    @Test
    fun `exact boundary is readable and one extra byte is rejected`() {
        val exact = BoundedArchiveInputStream(ByteArrayInputStream(byteArrayOf(1, 2, 3, 4)), 4)
        assertArrayEquals(byteArrayOf(1, 2, 3, 4), exact.readBytes())

        val oversized = BoundedArchiveInputStream(ByteArrayInputStream(byteArrayOf(1, 2, 3, 4, 5)), 4)
        val output = ByteArray(4)
        assertEquals(4, oversized.read(output))
        try {
            oversized.read()
            throw AssertionError("expected archive budget failure")
        } catch (_: IOException) {
        }
    }
}
