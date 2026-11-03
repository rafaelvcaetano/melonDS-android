package me.magnum.melonds.common.cheats

import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import kotlin.math.min

class ProgressTrackerInputStream(inputStream: InputStream?) : FilterInputStream(inputStream) {
    var totalReadBytes = 0
        private set
    private var maximumReadBytes = Long.MAX_VALUE

    fun setMaximumReadBytes(maximumReadBytes: Long) {
        require(maximumReadBytes >= totalReadBytes)
        this.maximumReadBytes = min(this.maximumReadBytes, maximumReadBytes)
    }

    override fun read(): Int {
        val value = super.read()
        if (value == -1) return -1
        if (totalReadBytes.toLong() >= maximumReadBytes) {
            throw IOException("Input exceeds $maximumReadBytes bytes")
        }
        totalReadBytes++
        return value
    }

    override fun read(b: ByteArray?, off: Int, len: Int): Int {
        if (len == 0) return 0
        val remaining = maximumReadBytes - totalReadBytes
        if (remaining <= 0) return read()
        val readBytes = super.read(b, off, min(len.toLong(), remaining).toInt())
        if (readBytes > 0) totalReadBytes += readBytes

        return readBytes
    }
}
