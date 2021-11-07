package me.magnum.melonds.ui.emulator.rewind.model

import android.graphics.Bitmap
import me.magnum.melonds.utils.DsScreenshotConverter
import java.nio.ByteBuffer

class RewindSaveState(
    val buffer: ByteBuffer,
    val screenshotBuffer: ByteBuffer,
    val frame: Int,
) {
    val screenshot: Bitmap get() = DsScreenshotConverter.fromByteBufferToBitmap(screenshotBuffer)
}