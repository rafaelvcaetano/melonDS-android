package me.magnum.melonds.utils

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri

object BitmapUtils {
    fun calculateMinimumSampleSize(context: Context, bitmapUri: Uri, targetWidth: Int, targetHeight: Int): Int {
        val options = BitmapFactory.Options()
        options.inJustDecodeBounds = true

        try {
            context.contentResolver.openInputStream(bitmapUri)?.use {
                BitmapFactory.decodeStream(it, null, options)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            return 1
        }

        if (options.outWidth == -1 || options.outHeight == -1) {
            return 1
        }

        val bitmapWidth = options.outWidth
        val bitmapHeight = options.outHeight
        return calculateMinimumSampleSize(bitmapWidth, bitmapHeight, targetWidth, targetHeight)
    }

    fun calculateMinimumSampleSize(bitmapWidth: Int, bitmapHeight: Int, targetWidth: Int, targetHeight: Int): Int {
        var inSampleSize = 1
        if (bitmapHeight > targetHeight || bitmapWidth > targetWidth) {

            val halfHeight: Int = bitmapHeight / 2
            val halfWidth: Int = bitmapWidth / 2

            // Calculate the largest inSampleSize value that is a power of 2 and keeps both
            // height and width larger than the requested height and width.
            while (halfHeight / inSampleSize >= targetHeight && halfWidth / inSampleSize >= targetWidth) {
                inSampleSize *= 2
            }
        }

        return inSampleSize
    }
}