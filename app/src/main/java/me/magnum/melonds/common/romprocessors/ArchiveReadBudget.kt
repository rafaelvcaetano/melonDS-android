package me.magnum.melonds.common.romprocessors

import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import kotlin.math.min

internal object ArchiveReadBudget {
    const val MAX_ENTRIES = 4096
    const val MAX_DISCOVERY_BYTES = 64L * 1024L * 1024L
    const val MAX_ROM_BYTES = 512L * 1024L * 1024L
}

internal class BoundedArchiveInputStream(
    input: InputStream,
    private val maximumBytes: Long,
) : FilterInputStream(input) {
    private var bytesRead = 0L

    override fun read(): Int {
        val value = super.read()
        if (value == -1) return -1
        if (bytesRead >= maximumBytes) throw IOException("Archive output exceeds $maximumBytes bytes")
        bytesRead++
        return value
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        val remaining = maximumBytes - bytesRead
        if (remaining <= 0) return read()
        val count = super.read(buffer, offset, min(length.toLong(), remaining).toInt())
        if (count > 0) bytesRead += count
        return count
    }
}
