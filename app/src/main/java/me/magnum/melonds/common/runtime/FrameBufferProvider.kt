package me.magnum.melonds.common.runtime

import android.graphics.Bitmap
import me.magnum.melonds.domain.model.RendererConfiguration
import java.nio.ByteBuffer
import java.nio.ByteOrder

class FrameBufferProvider {

    companion object {
        private const val SCREEN_WIDTH = 256
        private const val SCREEN_HEIGHT = 384
    }

    private var rendererConfiguration: RendererConfiguration? = null

    private var frameBuffer: ByteBuffer? = null

    fun setRendererConfiguration(configuration: RendererConfiguration) {
        val mustResizeFrameBuffer = isFrameBufferReady() && rendererConfiguration?.resolutionScaling != configuration.resolutionScaling
        rendererConfiguration = configuration

        if (mustResizeFrameBuffer) {
            frameBuffer = null
            ensureFrameBufferIsReady()
        }
    }

    fun isFrameBufferReady(): Boolean {
        return frameBuffer != null
    }

    fun frameBuffer(): ByteBuffer {
        return ensureFrameBufferIsReady()
    }

    fun getScreenshot(): Bitmap {
        val frameBuffer = ensureFrameBufferIsReady()

        val scale = rendererConfiguration?.resolutionScaling ?: 1
        val scaledWidth = SCREEN_WIDTH * scale

        return Bitmap.createBitmap(SCREEN_WIDTH, SCREEN_HEIGHT, Bitmap.Config.ARGB_8888).apply {
            for (x in 0 until SCREEN_WIDTH) {
                for (y in 0 until SCREEN_HEIGHT) {
                    val pixelPosition = (y * scale * scaledWidth + x * scale) * 4
                    // There's no need to do a manual pixel format conversion. Since getInt() uses the buffer's byte order, which is little endian, it will automatically
                    // convert the internal BGRA format into the ARGB format, which is what we need to build the bitmap
                    val argbPixel = frameBuffer.getInt(pixelPosition)
                    setPixel(x, y, argbPixel)
                }
            }
        }
    }

    fun clearFrameBuffer() {
        frameBuffer?.let { buffer ->
            buffer.position(0)
            repeat(buffer.capacity() / 4) {
                buffer.putInt(0xFF000000.toInt())
            }
        }
    }

    private fun ensureFrameBufferIsReady(): ByteBuffer {
        if (frameBuffer != null) {
            return frameBuffer!!
        }

        val rendererConfiguration = requireNotNull(rendererConfiguration)
        val scaledWidth = SCREEN_WIDTH * rendererConfiguration.resolutionScaling
        val scaledHeight= SCREEN_HEIGHT * rendererConfiguration.resolutionScaling
        frameBuffer = ByteBuffer.allocateDirect(scaledWidth * scaledHeight * 4).order(ByteOrder.nativeOrder())
        return frameBuffer!!
    }
}