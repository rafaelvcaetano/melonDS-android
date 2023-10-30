package me.magnum.melonds.common.camera

class BlackDSiCameraSource : DSiCameraSource {

    override fun startCamera(camera: CameraType) {
    }

    override fun stopCamera(camera: CameraType) {
    }

    override fun captureFrame(camera: CameraType, buffer: ByteArray, width: Int, height: Int, isYuv: Boolean) {
        for (i in buffer.indices step 2) {
            // Use 0 for luminance (Y) and 127 for color (U and V)
            buffer[i] = 0
            buffer[i + 1] = 127
        }
    }

    override fun dispose() {
    }
}