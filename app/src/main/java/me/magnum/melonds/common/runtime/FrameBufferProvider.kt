package me.magnum.melonds.common.runtime

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
}