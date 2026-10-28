package me.magnum.melonds.common.cheats

import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/** Provider-like read that deliberately ignores interrupt: only close releases it. */
class CloseAwareBlockingInputStream(bytes: ByteArray, private val blockAt: Int = 0x100) : InputStream() {
    private val source = ByteArrayInputStream(bytes)
    private val entered = CountDownLatch(1)
    private val released = CountDownLatch(1)
    private var position = 0
    val closed = AtomicBoolean()

    fun awaitRead(): Boolean = entered.await(5, TimeUnit.SECONDS)

    private fun beforeRead() {
        if (position >= blockAt) {
            entered.countDown()
            while (released.count > 0) {
                try { released.await() } catch (_: InterruptedException) { /* Model a non-interruptible provider. */ }
            }
        }
        if (closed.get()) throw IOException("Provider stream closed")
    }

    override fun read(): Int {
        beforeRead()
        return source.read().also { if (it >= 0) position++ }
    }

    override fun read(target: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        beforeRead()
        val count = if (position < blockAt) minOf(length, blockAt - position) else length
        return source.read(target, offset, count).also { if (it > 0) position += it }
    }

    override fun close() {
        closed.set(true)
        released.countDown()
    }
}
