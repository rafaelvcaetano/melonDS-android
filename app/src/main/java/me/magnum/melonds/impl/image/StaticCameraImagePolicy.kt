package me.magnum.melonds.impl.image

import kotlin.math.max

internal object StaticCameraImagePolicy {
    const val TARGET_WIDTH = 640
    const val TARGET_HEIGHT = 480

    private const val MAX_DIMENSION = 8192
    private const val MAX_PIXELS = 32L * 1024L * 1024L
    private const val MAX_ASPECT_RATIO = 16

    data class Crop(val left: Int, val top: Int, val width: Int, val height: Int)

    fun accepts(width: Int, height: Int): Boolean {
        if (width <= 0 || height <= 0 || width > MAX_DIMENSION || height > MAX_DIMENSION) {
            return false
        }
        if (width.toLong() * height.toLong() > MAX_PIXELS) {
            return false
        }
        val longSide = max(width, height).toLong()
        val shortSide = minOf(width, height).toLong()
        return longSide <= shortSide * MAX_ASPECT_RATIO
    }

    fun centerCrop(width: Int, height: Int): Crop {
        return if (width.toLong() * TARGET_HEIGHT > height.toLong() * TARGET_WIDTH) {
            val cropWidth = (height.toLong() * TARGET_WIDTH / TARGET_HEIGHT).toInt()
            Crop((width - cropWidth) / 2, 0, cropWidth, height)
        } else {
            val cropHeight = (width.toLong() * TARGET_HEIGHT / TARGET_WIDTH).toInt()
            Crop(0, (height - cropHeight) / 2, width, cropHeight)
        }
    }

    fun sampleSize(width: Int, height: Int): Int {
        var sample = 1
        while (width / (sample * 2) >= TARGET_WIDTH && height / (sample * 2) >= TARGET_HEIGHT) {
            sample *= 2
        }
        return sample
    }
}
