package me.magnum.melonds.common.runtime

import android.graphics.Bitmap
import java.nio.ByteBuffer
import java.nio.ByteOrder

class ScreenshotFrameBufferProvider {

    companion object {
        private const val SCREEN_WIDTH = 256
        private const val SCREEN_HEIGHT = 384
    }

    private var screenshotBuffer: ByteBuffer? = null

    fun frameBuffer(): ByteBuffer {
        return ensureBufferIsReady()
    }

    fun getScreenshot(): Bitmap {
        val frameBuffer = ensureBufferIsReady()

        return Bitmap.createBitmap(SCREEN_WIDTH, SCREEN_HEIGHT, Bitmap.Config.ARGB_8888).apply {
            for (x in 0 until SCREEN_WIDTH) {
                for (y in 0 until SCREEN_HEIGHT) {
                    val pixelPosition = (y * SCREEN_WIDTH + x) * 4
                    // There's no need to do a manual pixel format conversion. Since getInt() uses the buffer's byte order, which is little endian, it will automatically
                    // convert the internal BGRA format into the ARGB format, which is what we need to build the bitmap
                    val argbPixel = frameBuffer.getInt(pixelPosition)
                    setPixel(x, y, argbPixel)
                }
            }
        }
    }

    fun clearBuffer() {
        screenshotBuffer?.let { buffer ->
            buffer.position(0)
            repeat(buffer.capacity() / 4) {
                buffer.putInt(0xFF000000.toInt())
            }
        }
    }

    private fun ensureBufferIsReady(): ByteBuffer {
        if (screenshotBuffer != null) {
            return screenshotBuffer!!
        }

        screenshotBuffer = ByteBuffer.allocateDirect(SCREEN_WIDTH * SCREEN_HEIGHT * 4).order(ByteOrder.nativeOrder())
        return screenshotBuffer!!
    }
}