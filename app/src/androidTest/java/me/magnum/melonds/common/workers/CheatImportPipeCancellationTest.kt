package me.magnum.melonds.common.workers

import android.os.ParcelFileDescriptor
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import me.magnum.melonds.common.cheats.ProgressTrackerInputStream
import me.magnum.melonds.common.cheats.UsrCheatDatabaseParser
import me.magnum.melonds.common.cheats.UsrCheatFixture
import me.magnum.melonds.common.cheats.importEvents
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.FilterInputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/** Actual Android pipe and libcore read/close, rather than a provider-like JVM stream. */
@RunWith(AndroidJUnit4::class)
class CheatImportPipeCancellationTest {
    @Test(timeout = 15000)
    fun cancellationClosesAndroidPipeAndStopsParserWhileWriterRemainsOpen() = runBlocking {
        val pipe = ParcelFileDescriptor.createPipe()
        val writer = ParcelFileDescriptor.AutoCloseOutputStream(pipe[1])
        writer.write(UsrCheatFixture.manyGames(100), 0, 0x100)
        val waitingForAddressBook = CountDownLatch(1)
        val closed = AtomicBoolean()
        val input = object : FilterInputStream(ParcelFileDescriptor.AutoCloseInputStream(pipe[0])) {
            private var count = 0
            override fun read(bytes: ByteArray, offset: Int, length: Int): Int {
                if (count == 0x100) waitingForAddressBook.countDown()
                return `in`.read(bytes, offset, length).also { if (it > 0) count += it }
            }
            override fun close() { closed.set(true); super.close() }
        }
        val parsing = launch(Dispatchers.IO) {
            try {
                UsrCheatDatabaseParser().importEvents(ProgressTrackerInputStream(input)) {}.toList()
            } catch (error: Exception) {
                // A failed baseline is released by writer EOF only after the oracle.
                currentCoroutineContext().ensureActive()
                throw error
            }
        }
        var stopped = false
        var closedOnCancel = false
        try {
            assertTrue("Parser reached the real pipe's empty address-book read",
                withContext(Dispatchers.IO) { waitingForAddressBook.await(5, TimeUnit.SECONDS) })
            parsing.cancel()
            stopped = withTimeoutOrNull(2000) { parsing.join(); true } == true
            closedOnCancel = closed.get()
        } finally {
            // The writer is deliberately kept open until after the cancellation oracle.
            writer.close()
            input.close()
            parsing.cancelAndJoin()
        }
        assertTrue("Cancellation must close the Android descriptor", closedOnCancel)
        assertTrue("Cancellation must release the blocked read without provider EOF", stopped)
    }
}
