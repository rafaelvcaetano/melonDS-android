package me.magnum.melonds.impl.emulator

import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EmulatorEventFrameDecoderTest {

    @Test
    fun retainsPartialHeaderAndPayload() {
        val decoder = EmulatorEventFrameDecoder()
        val frame = frame(EmulatorEventType.EventRumbleStart.event, intPayload(250))
        val events = mutableListOf<Pair<EmulatorEventType, ByteArray>>()

        decoder.consume(frame.slice(0, 3)) { type, data -> events += type to data.toByteArray() }
        decoder.consume(frame.slice(3, 9)) { type, data -> events += type to data.toByteArray() }
        decoder.consume(frame.slice(9, frame.size)) { type, data -> events += type to data.toByteArray() }

        assertEquals(1, events.size)
        assertEquals(EmulatorEventType.EventRumbleStart, events.single().first)
        assertEquals(250, ByteBuffer.wrap(events.single().second).order(ByteOrder.nativeOrder()).int)
    }

    @Test
    fun parsesCoalescedFrames() {
        val first = frame(EmulatorEventType.EventRumbleStop.event, byteArrayOf())
        val second = frame(EmulatorEventType.EventEmulatorStop.event, intPayload(4))
        val events = mutableListOf<EmulatorEventType>()

        EmulatorEventFrameDecoder().consume(ByteBuffer.wrap(first + second)) { type, _ ->
            events += type
        }

        assertEquals(
            listOf(EmulatorEventType.EventRumbleStop, EmulatorEventType.EventEmulatorStop),
            events,
        )
    }

    @Test
    fun discardsUnknownTypeWithoutLosingFollowingFrame() {
        val unknown = frame(999, byteArrayOf(1, 2, 3))
        val valid = frame(EmulatorEventType.EventRumbleStop.event, byteArrayOf())
        val events = mutableListOf<EmulatorEventType>()

        EmulatorEventFrameDecoder().consume(ByteBuffer.wrap(unknown + valid)) { type, _ ->
            events += type
        }

        assertEquals(listOf(EmulatorEventType.EventRumbleStop), events)
    }

    @Test
    fun rejectsOversizedPayload() {
        val invalidHeader = header(EmulatorMessageQueue.MAX_DATA_SIZE_BYTES + 1)

        var failed = false
        try {
            EmulatorEventFrameDecoder().consume(ByteBuffer.wrap(invalidHeader)) { _, _ -> }
        } catch (exception: IllegalArgumentException) {
            failed = true
        }

        assertTrue(failed)
    }

    @Test
    fun rejectsNegativePayload() {
        var failed = false
        try {
            EmulatorEventFrameDecoder().consume(ByteBuffer.wrap(header(-1))) { _, _ -> }
        } catch (exception: IllegalArgumentException) {
            failed = true
        }

        assertTrue(failed)
    }

    private fun frame(type: Int, payload: ByteArray): ByteArray {
        return ByteBuffer.allocate(Int.SIZE_BYTES * 2 + payload.size)
            .order(ByteOrder.nativeOrder())
            .putInt(type)
            .putInt(payload.size)
            .put(payload)
            .array()
    }

    private fun intPayload(value: Int): ByteArray {
        return ByteBuffer.allocate(Int.SIZE_BYTES)
            .order(ByteOrder.nativeOrder())
            .putInt(value)
            .array()
    }

    private fun header(payloadLength: Int): ByteArray {
        return ByteBuffer.allocate(Int.SIZE_BYTES * 2)
            .order(ByteOrder.nativeOrder())
            .putInt(EmulatorEventType.EventRumbleStart.event)
            .putInt(payloadLength)
            .array()
    }

    private fun ByteBuffer.toByteArray(): ByteArray {
        return ByteArray(remaining()).also { get(it) }
    }

    private fun ByteArray.slice(start: Int, end: Int): ByteBuffer {
        return ByteBuffer.wrap(copyOfRange(start, end))
    }
}
