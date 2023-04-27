package me.magnum.melonds.common.runtime

import android.graphics.Bitmap
import java.nio.ByteBuffer
import java.nio.ByteOrder

class FrameBufferProvider {

    companion object {
        private const val SCREEN_WIDTH = 256
        private const val SCREEN_HEIGHT = 384
    }

    private val frameBuffer by lazy {
        ByteBuffer.allocateDirect(SCREEN_WIDTH * SCREEN_HEIGHT * 4).order(ByteOrder.nativeOrder())
    }

    fun frameBuffer(): ByteBuffer {
        return frameBuffer
    }

    fun getScreenshot(): Bitmap {
        return Bitmap.createBitmap(SCREEN_WIDTH, SCREEN_HEIGHT, Bitmap.Config.ARGB_8888).apply {
            // Texture buffer is in BGR format. Convert to RGB
            for (x in 0 until SCREEN_WIDTH) {
                for (y in 0 until SCREEN_HEIGHT) {
                    val b = frameBuffer[(y * SCREEN_WIDTH + x) * 4 + 0].toInt() and 0xFF
                    val g = frameBuffer[(y * SCREEN_WIDTH + x) * 4 + 1].toInt() and 0xFF
                    val r = frameBuffer[(y * SCREEN_WIDTH + x) * 4 + 2].toInt() and 0xFF
                    val argbPixel = 0xFF000000.toInt() or r.shl(16) or g.shl(8) or b
                    setPixel(x, y, argbPixel)
                }
            }
        }
    }

    fun clearFrameBuffer() {
        frameBuffer.position(0)
        repeat(frameBuffer.capacity() / 4) {
            frameBuffer.putInt(0xFF000000.toInt())
        }
    }
}