package me.magnum.melonds.common.streaming

import android.graphics.Bitmap
import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.ServerSocket
import java.net.Socket
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.max

class TopScreenStreamingServer(
    private val screenshotBuffer: ByteBuffer,
    private val width: Int = 256,
    private val height: Int = 192,
) {
    @Volatile
    private var running = false
    private var serverThread: Thread? = null
    private var serverSocket: ServerSocket? = null
    private var clientSocket: Socket? = null

    @Synchronized
    fun start(port: Int, fps: Int, quality: Int) {
        if (running) return
        running = true
        serverThread = Thread {
            runServer(port, fps, quality)
        }.apply { start() }
    }

    @Synchronized
    fun stop() {
        running = false
        try {
            clientSocket?.close()
        } catch (_: IOException) {
        }
        try {
            serverSocket?.close()
        } catch (_: IOException) {
        }
        clientSocket = null
        serverSocket = null
        serverThread?.join(500)
        serverThread = null
    }

    private fun runServer(port: Int, fps: Int, quality: Int) {
        val targetFrameMs = max(1, 1000 / max(1, fps))
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val header = ByteArray(16)
        val jpegStream = ByteArrayOutputStream(width * height)

        try {
            ServerSocket(port).use { server ->
                server.reuseAddress = true
                serverSocket = server
                while (running) {
                    val socket = server.accept()
                    clientSocket = socket
                    socket.tcpNoDelay = true
                    streamClient(socket, bitmap, header, jpegStream, targetFrameMs, quality)
                    clientSocket = null
                }
            }
        } catch (_: IOException) {
        }
    }

    private fun streamClient(
        socket: Socket,
        bitmap: Bitmap,
        header: ByteArray,
        jpegStream: ByteArrayOutputStream,
        targetFrameMs: Int,
        quality: Int,
    ) {
        val pixels = IntArray(width * height)
        try {
            val output = BufferedOutputStream(socket.getOutputStream(), 64 * 1024)
            while (running && !socket.isClosed) {
                val frameStart = System.nanoTime()
                val dup = screenshotBuffer.duplicate().order(ByteOrder.LITTLE_ENDIAN)
                // Screenshot buffer stores top screen first, so we only copy the first 256x192 region.
                dup.position(0)
                dup.limit(width * height * 4)
                dup.asIntBuffer().get(pixels, 0, pixels.size)
                bitmap.setPixels(pixels, 0, width, 0, 0, width, height)

                jpegStream.reset()
                if (!bitmap.compress(Bitmap.CompressFormat.JPEG, quality, jpegStream)) {
                    continue
                }
                val jpegBytes = jpegStream.toByteArray()
                writeHeader(header, jpegBytes.size)
                output.write(header)
                output.write(jpegBytes)
                output.flush()

                val elapsedMs = (System.nanoTime() - frameStart) / 1_000_000
                val sleepMs = targetFrameMs - elapsedMs
                if (sleepMs > 0) {
                    try {
                        Thread.sleep(sleepMs.toLong())
                    } catch (_: InterruptedException) {
                    }
                }
            }
        } catch (_: IOException) {
        } finally {
            try {
                socket.close()
            } catch (_: IOException) {
            }
        }
    }

    private fun writeHeader(buffer: ByteArray, jpegSize: Int) {
        buffer[0] = 'M'.code.toByte()
        buffer[1] = 'D'.code.toByte()
        buffer[2] = 'J'.code.toByte()
        buffer[3] = 'P'.code.toByte()
        writeIntLE(buffer, 4, 1)
        writeIntLE(buffer, 8, jpegSize)
        writeShortLE(buffer, 12, width)
        writeShortLE(buffer, 14, height)
    }

    private fun writeIntLE(buffer: ByteArray, offset: Int, value: Int) {
        buffer[offset] = (value and 0xFF).toByte()
        buffer[offset + 1] = ((value shr 8) and 0xFF).toByte()
        buffer[offset + 2] = ((value shr 16) and 0xFF).toByte()
        buffer[offset + 3] = ((value shr 24) and 0xFF).toByte()
    }

    private fun writeShortLE(buffer: ByteArray, offset: Int, value: Int) {
        buffer[offset] = (value and 0xFF).toByte()
        buffer[offset + 1] = ((value shr 8) and 0xFF).toByte()
    }
}
