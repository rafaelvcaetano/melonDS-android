package me.magnum.melonds.utils

import android.graphics.BitmapRegionDecoder
import android.os.Build
import java.io.InputStream

object BitmapRegionDecoderCompat {
    fun newInstance(inputStream: InputStream): BitmapRegionDecoder? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            BitmapRegionDecoder.newInstance(inputStream)
        } else {
            BitmapRegionDecoder.newInstance(inputStream, true)
        }
    }
}