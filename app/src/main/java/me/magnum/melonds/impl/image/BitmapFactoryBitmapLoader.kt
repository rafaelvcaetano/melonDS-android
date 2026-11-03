package me.magnum.melonds.impl.image

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri

class BitmapFactoryBitmapLoader(private val context: Context) : BitmapLoader {

    override fun loadAsBitmap(imageUri: Uri): Bitmap? {
        return try {
            context.contentResolver.openFileDescriptor(imageUri, "r").use { imageFileDescriptor ->
                if (imageFileDescriptor != null) {
                    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeFileDescriptor(imageFileDescriptor.fileDescriptor, null, bounds)
                    if (!StaticCameraImagePolicy.accepts(bounds.outWidth, bounds.outHeight)) {
                        return null
                    }

                    val options = BitmapFactory.Options().apply {
                        inSampleSize = StaticCameraImagePolicy.sampleSize(bounds.outWidth, bounds.outHeight)
                        inPreferredConfig = Bitmap.Config.ARGB_8888
                    }
                    val decoded = BitmapFactory.decodeFileDescriptor(imageFileDescriptor.fileDescriptor, null, options)
                        ?: return null
                    normalize(decoded)
                } else {
                    null
                }
            }
        } catch (e: Throwable) {
            null
        }
    }

    private fun normalize(decoded: Bitmap): Bitmap? {
        if (!StaticCameraImagePolicy.accepts(decoded.width, decoded.height)) {
            decoded.recycle()
            return null
        }

        val crop = StaticCameraImagePolicy.centerCrop(decoded.width, decoded.height)
        var working = if (crop.left == 0 && crop.top == 0 && crop.width == decoded.width && crop.height == decoded.height) {
            decoded
        } else {
            Bitmap.createBitmap(decoded, crop.left, crop.top, crop.width, crop.height).also {
                if (it !== decoded) decoded.recycle()
            }
        }

        if (working.width != StaticCameraImagePolicy.TARGET_WIDTH || working.height != StaticCameraImagePolicy.TARGET_HEIGHT) {
            working = Bitmap.createScaledBitmap(
                working,
                StaticCameraImagePolicy.TARGET_WIDTH,
                StaticCameraImagePolicy.TARGET_HEIGHT,
                true,
            ).also { scaled ->
                if (scaled !== working) working.recycle()
            }
        }
        return working.takeIf {
            !it.isRecycled &&
                it.width == StaticCameraImagePolicy.TARGET_WIDTH &&
                it.height == StaticCameraImagePolicy.TARGET_HEIGHT
        }
    }
}
