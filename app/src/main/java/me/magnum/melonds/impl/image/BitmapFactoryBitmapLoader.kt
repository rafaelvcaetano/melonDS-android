package me.magnum.melonds.impl.image

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import kotlin.math.roundToInt

class BitmapFactoryBitmapLoader(private val context: Context) : BitmapLoader {

    override fun loadAsBitmap(imageUri: Uri): Bitmap? {
        return try {
            context.contentResolver.openFileDescriptor(imageUri, "r").use { imageFileDescriptor ->
                if (imageFileDescriptor != null) {
                    val originalBitmap = BitmapFactory.decodeFileDescriptor(imageFileDescriptor.fileDescriptor)

                    val cameraAspectRatio = 640 / 480f
                    val sourceAspectRatio = originalBitmap.width / originalBitmap.height.toFloat()

                    val scale = if (sourceAspectRatio > cameraAspectRatio) {
                        480f / originalBitmap.height
                    } else {
                        originalBitmap.width / 640f
                    }

                    val scaledBitmap = Bitmap.createScaledBitmap(originalBitmap, (originalBitmap.width * scale).roundToInt(), (originalBitmap.height * scale).roundToInt(), true)
                    if (!originalBitmap.sameAs(scaledBitmap)) {
                        scaledBitmap.recycle()
                    }

                    Bitmap.createBitmap(scaledBitmap, (scaledBitmap.width - 640) / 2, (scaledBitmap.height - 480) / 2, 640, 480).also {
                        if (!scaledBitmap.sameAs(it)) {
                            it.recycle()
                        }
                    }
                } else {
                    null
                }
            }
        } catch (e: Throwable) {
            null
        }
    }
}