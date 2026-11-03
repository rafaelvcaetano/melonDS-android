package me.magnum.melonds.impl.image

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.graphics.Rect
import android.net.Uri
import android.os.Build
import androidx.annotation.RequiresApi

@RequiresApi(Build.VERSION_CODES.P)
class ImageDecoderBitmapLoader(private val context: Context) : BitmapLoader {

    override fun loadAsBitmap(imageUri: Uri): Bitmap? {
        val source = ImageDecoder.createSource(context.contentResolver, imageUri)
        return try {
            ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                if (!StaticCameraImagePolicy.accepts(info.size.width, info.size.height)) {
                    throw IllegalArgumentException("Unsupported camera image dimensions")
                }
                val crop = StaticCameraImagePolicy.centerCrop(info.size.width, info.size.height)
                decoder.crop = Rect(crop.left, crop.top, crop.left + crop.width, crop.top + crop.height)
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                decoder.memorySizePolicy = ImageDecoder.MEMORY_POLICY_LOW_RAM
                decoder.setTargetSize(StaticCameraImagePolicy.TARGET_WIDTH, StaticCameraImagePolicy.TARGET_HEIGHT)
            }.let { decoded ->
                if (decoded.width != StaticCameraImagePolicy.TARGET_WIDTH || decoded.height != StaticCameraImagePolicy.TARGET_HEIGHT) {
                    decoded.recycle()
                    null
                } else if (decoded.config == Bitmap.Config.ARGB_8888) {
                    decoded
                } else {
                    decoded.copy(Bitmap.Config.ARGB_8888, false).also { decoded.recycle() }
                }
            }
        } catch (e: Throwable) {
            null
        }
    }
}
