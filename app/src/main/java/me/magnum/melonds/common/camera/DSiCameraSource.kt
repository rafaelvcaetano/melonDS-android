package me.magnum.melonds.common.camera

typealias CameraType = Int

interface DSiCameraSource {

    companion object {
        const val BackCamera: CameraType = 0
        const val FrontCamera: CameraType = 1
    }

    fun startCamera(camera: CameraType)
    fun stopCamera(camera: CameraType)
    fun captureFrame(camera: CameraType, buffer: ByteArray, width: Int, height: Int, isYuv: Boolean)
    fun dispose()
}