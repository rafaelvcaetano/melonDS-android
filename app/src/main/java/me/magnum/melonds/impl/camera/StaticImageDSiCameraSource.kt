package me.magnum.melonds.impl.camera

import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.core.graphics.blue
import androidx.core.graphics.green
import androidx.core.graphics.red
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.magnum.melonds.R
import me.magnum.melonds.common.camera.CameraType
import me.magnum.melonds.common.camera.DSiCameraSource
import me.magnum.melonds.domain.repositories.SettingsRepository
import me.magnum.melonds.impl.image.BitmapLoader

class StaticImageDSiCameraSource(
    private val context: Context,
    private val settingsRepository: SettingsRepository,
    private val bitmapLoader: BitmapLoader,
) : DSiCameraSource {

    private val coroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var imageObserveJob: Job? = null
    private val currentImageBuffer = ByteArray(640 * 480 * 2)

    @RequiresApi(Build.VERSION_CODES.P)
    override fun startCamera(camera: CameraType) {
        imageObserveJob = coroutineScope.launch {
            settingsRepository.observeDSiCameraStaticImage().collectLatest {
                if (it == null) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, R.string.no_image_selected, Toast.LENGTH_SHORT).show()
                    }
                } else {
                    val bitmap = bitmapLoader.loadAsBitmap(it)
                    if (bitmap == null) {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(context, R.string.failed_to_load_image, Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        loadBitmapIntoBuffer(bitmap)
                        bitmap.recycle()
                    }
                }
            }
        }
    }

    private fun loadBitmapIntoBuffer(bitmap: Bitmap) {
        for (y in 0 until bitmap.height) {
            for (x in 0 until bitmap.width step 2) {
                val pixel1 = bitmap.getPixel(x, y)
                val pixel2 = bitmap.getPixel(x + 1, y)

                val y1 = ((66 * pixel1.red + 129 * pixel1.green +  25 * pixel1.blue + 128) shr 8) + 16
                val y2 = ((66 * pixel2.red + 129 * pixel2.green +  25 * pixel2.blue + 128) shr 8) + 16
                val u = ((-38 * pixel1.red - 74 * pixel1.green + 112 * pixel1.blue + 128) shr 8) + 128
                val v = ((112 * pixel1.red - 94 * pixel1.green - 18 * pixel1.blue + 128) shr 8) + 128

                currentImageBuffer[y * bitmap.width * 2 + x * 2 + 0] = y1.toByte()
                currentImageBuffer[y * bitmap.width * 2 + x * 2 + 1] = u.toByte()
                currentImageBuffer[y * bitmap.width * 2 + x * 2 + 2] = y2.toByte()
                currentImageBuffer[y * bitmap.width * 2 + x * 2 + 3] = v.toByte()
            }
        }
    }

    override fun stopCamera(camera: CameraType) {
        imageObserveJob?.cancel()
        imageObserveJob = null
    }

    override fun captureFrame(camera: CameraType, buffer: ByteArray, width: Int, height: Int, isYuv: Boolean) {
        System.arraycopy(currentImageBuffer, 0, buffer, 0, currentImageBuffer.size)
    }

    override fun dispose() {
        coroutineScope.cancel()
    }
}