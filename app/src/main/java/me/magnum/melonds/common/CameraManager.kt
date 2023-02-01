package me.magnum.melonds.common

typealias CameraType = Int

interface CameraManager {

    companion object {
        const val BackCamera: CameraType = 0
        const val FrontCamera: CameraType = 1
    }

    fun startCamera(camera: CameraType)
    fun stopCamera(camera: CameraType)
    fun captureFrame(camera: CameraType, buffer: ByteArray, width: Int, height: Int, isYuv: Boolean)
}