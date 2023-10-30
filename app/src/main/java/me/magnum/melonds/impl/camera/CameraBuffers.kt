package me.magnum.melonds.impl.camera

class CameraBuffers {
    private val cameraBuffers = Array(2) {
        // YUV 422 format. Byte structure: YUYV YUYV (4 bytes per 2 pixels)
        ByteArray(640 * 480 * 2)
    }
    private var activeBufferIndex = 0

    fun getFrontBuffer(): ByteArray {
        return cameraBuffers[activeBufferIndex]
    }

    fun getBackBuffer(): ByteArray {
        return cameraBuffers[(activeBufferIndex + 1) % cameraBuffers.size]
    }

    fun swapBuffers() {
        activeBufferIndex = (activeBufferIndex + 1) % cameraBuffers.size
    }
}