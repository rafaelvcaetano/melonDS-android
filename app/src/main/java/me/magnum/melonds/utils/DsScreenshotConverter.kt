package me.magnum.melonds.utils

import android.graphics.Bitmap
import java.nio.ByteBuffer

object DsScreenshotConverter {
    private const val SCREEN_WIDTH = 256
    private const val SCREEN_HEIGHT = 384

    fun fromByteBufferToBitmap(buffer: ByteBuffer): Bitmap {
        return Bitmap.createBitmap(SCREEN_WIDTH, SCREEN_HEIGHT, Bitmap.Config.ARGB_8888).apply {
            // Texture buffer is in BGR format. Convert to RGB
            for (x in 0 until SCREEN_WIDTH) {
                for (y in 0 until SCREEN_HEIGHT) {
                    val b = buffer[(y * SCREEN_WIDTH + x) * 4 + 0].toInt() and 0xFF
                    val g = buffer[(y * SCREEN_WIDTH + x) * 4 + 1].toInt() and 0xFF
                    val r = buffer[(y * SCREEN_WIDTH + x) * 4 + 2].toInt() and 0xFF
                    val argbPixel = 0xFF000000.toInt() or r.shl(16) or g.shl(8) or b
                    setPixel(x, y, argbPixel)
                }
            }
        }
    }
}