package me.magnum.melonds.impl.image

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.graphics.Point
import android.graphics.Rect
import android.net.Uri
import android.os.Build
import androidx.annotation.RequiresApi
import java.io.IOException
import kotlin.math.roundToInt

@RequiresApi(Build.VERSION_CODES.P)
class ImageDecoderBitmapLoader(private val context: Context) : BitmapLoader {

    override fun loadAsBitmap(imageUri: Uri): Bitmap? {
        val source = ImageDecoder.createSource(context.contentResolver, imageUri)
        return try {
            ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                val cameraAspectRatio = 640 / 480f
                val sourceAspectRatio = info.size.width / info.size.height.toFloat()

                val (cropRect, outputSize) = if (sourceAspectRatio > cameraAspectRatio) {
                    val scale = 480f / info.size.height

                    val rect = Rect(
                        (info.size.width * scale / 2 - 320).roundToInt(),
                        0,
                        (info.size.width * scale / 2 + 320).roundToInt(),
                        480,
                    )
                    rect to Point((info.size.width * scale).roundToInt(), 480)
                } else {
                    val scale = info.size.width / 640f
                    val rect = Rect(
                        0,
                        (info.size.height * scale / 2f - 240).roundToInt(),
                        640,
                        (info.size.height * scale / 2f + 240).roundToInt()
                    )
                    rect to Point(640, (info.size.height * scale).roundToInt())
                }

                decoder.crop = cropRect
                decoder.setTargetSize(outputSize.x, outputSize.y)
            }.copy(Bitmap.Config.ARGB_8888, false)
        } catch (e: IOException) {
            null
        }
    }
}