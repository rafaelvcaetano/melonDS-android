package me.magnum.melonds.impl

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import me.magnum.melonds.domain.model.Background
import me.magnum.melonds.utils.BitmapUtils
import java.io.File

class BackgroundThumbnailProvider(private val context: Context) {
    companion object {
        private const val THUMBNAIL_SIZE = 256
        private const val THUMBNAIL_CACHE_DIR = "background_thumbnails"
    }

    fun getBackgroundThumbnail(background: Background): Bitmap? {
        return loadThumbnailFromDisk(background)
    }

    private fun loadThumbnailFromDisk(background: Background): Bitmap? {
        val thumbnailFile = getThumbnailFile(background)
        if (thumbnailFile?.isFile == true) {
            return BitmapFactory.decodeFile(thumbnailFile.absolutePath)
        }

        val thumbnail = generateBackgroundThumbnail(background)
        if (thumbnail != null) {
            saveThumbnailToDisk(background, thumbnail)
        }

        return thumbnail
    }

    private fun saveThumbnailToDisk(background: Background, thumbnail: Bitmap) {
        try {
            val thumbnailFile = getThumbnailFile(background) ?: return
            thumbnailFile.outputStream().use {
                thumbnail.compress(Bitmap.CompressFormat.PNG, 100, it)
            }
        } catch (_: Exception) {
            // Ignore errors
        }
    }

    private fun getThumbnailFile(background: Background): File? {
        val cacheDir = context.externalCacheDir?.let { File(it, THUMBNAIL_CACHE_DIR) } ?: return null
        return if (cacheDir.isDirectory || cacheDir.mkdirs()) {
            File(cacheDir, background.id.toString())
        } else {
            null
        }
    }

    private fun generateBackgroundThumbnail(background: Background): Bitmap? {
        val options = BitmapFactory.Options()
        options.inJustDecodeBounds = true

        try {
            context.contentResolver.openInputStream(background.uri)?.use {
                BitmapFactory.decodeStream(it, null, options)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }

        if (options.outWidth == -1 || options.outHeight == -1) {
            return null
        }

        // Find thumbnail dimensions limiting the largest side to THUMBNAIL_SIZE
        val (thumbnailWidth: Int, thumbnailHeight: Int) = if (options.outWidth > options.outHeight) {
            (THUMBNAIL_SIZE to (THUMBNAIL_SIZE * (options.outHeight / options.outWidth.toFloat())).toInt())
        } else {
            ((THUMBNAIL_SIZE * (options.outWidth / options.outHeight.toFloat())).toInt() to THUMBNAIL_SIZE)
        }

        val sampleSize = BitmapUtils.calculateMinimumSampleSize(options.outWidth, options.outHeight, thumbnailWidth, thumbnailHeight)
        options.inJustDecodeBounds = false
        options.inSampleSize = sampleSize

        return try {
            context.contentResolver.openInputStream(background.uri)?.use {
                BitmapFactory.decodeStream(it, null, options)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}