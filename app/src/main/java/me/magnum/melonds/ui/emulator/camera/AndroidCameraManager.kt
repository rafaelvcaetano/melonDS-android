package me.magnum.melonds.ui.emulator.camera

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Matrix
import android.os.Handler
import android.os.Looper
import android.util.Size
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageInfo
import androidx.camera.core.ImageProxy.PlaneProxy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import me.magnum.melonds.common.CameraManager
import me.magnum.melonds.common.CameraType
import java.nio.ByteBuffer
import java.util.Arrays
import java.util.concurrent.Executors

class AndroidCameraManager(
    private val context: Context,
    private val lifecycleOwner: LifecycleOwner,
    private val onRequestCameraPermission: (callback: () -> Unit) -> Unit
) : CameraManager {

    private var currentCameraProvider: ProcessCameraProvider? = null
    private val cameraBuffers = CameraBuffers()
    private val executor = Executors.newSingleThreadExecutor()
    private val handler = Handler(Looper.getMainLooper())
    private val originalPoints = FloatArray(640 * 480 * 2)
    private val mappedPoints = FloatArray(640 * 480 * 2)

    override fun startCamera(camera: CameraType) {
        initializeOriginalPoints()
        Arrays.fill(cameraBuffers.getFrontBuffer(), 0)
        if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            onRequestCameraPermission {
                initializeCamera(camera)
            }
        } else {
            handler.post {
                initializeCamera(camera)
            }
        }
    }

    override fun stopCamera(camera: CameraType) {
        handler.post {
            currentCameraProvider?.unbindAll()
            currentCameraProvider = null
        }
    }

    override fun captureFrame(camera: CameraType, buffer: ByteArray, width: Int, height: Int, isYuv: Boolean) {
        val currentFrontBuffer = cameraBuffers.getFrontBuffer()
        System.arraycopy(currentFrontBuffer, 0, buffer, 0, currentFrontBuffer.size)
    }

    private fun initializeCamera(camera: CameraType) {
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener(
            {
                val cameraProvider = future.get()

                val analyzer = ImageAnalysis.Builder()
                    .setTargetResolution(Size(640, 480))
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()

                analyzer.setAnalyzer(executor) { imageProxy ->
                    val yBuffer = imageProxy.planes[0].buffer
                    val uPlane = imageProxy.planes[1]
                    val vPlane = imageProxy.planes[2]

                    yBuffer.rewind()
                    uPlane.buffer.rewind()
                    vPlane.buffer.rewind()

                    captureFrameSample(yBuffer, uPlane, vPlane, imageProxy.width, imageProxy.height, imageProxy.imageInfo)

                    imageProxy.close()
                }

                val cameraSelector = when (camera) {
                    CameraManager.FrontCamera -> CameraSelector.DEFAULT_FRONT_CAMERA
                    CameraManager.BackCamera -> CameraSelector.DEFAULT_BACK_CAMERA
                    else -> throw UnsupportedOperationException("Unknown camera type $camera")
                }

                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(lifecycleOwner, cameraSelector, analyzer)
                currentCameraProvider = cameraProvider
            },
            ContextCompat.getMainExecutor(context)
        )
    }

    private fun captureFrameSample(yBuffer: ByteBuffer, uPlane: PlaneProxy, vPlane: PlaneProxy, sourceWidth: Int, sourceHeight: Int, imageInfo: ImageInfo) {
        val (realWidth, realHeight) = if (imageInfo.rotationDegrees == 90 || imageInfo.rotationDegrees == 270) {
            sourceHeight to sourceWidth
        } else {
            sourceWidth to sourceHeight
        }
        val targetAspectRatio =  640 / 480f
        val sourceAspectRatio = realWidth / realHeight.toFloat()
        val scaleToFill = if (sourceAspectRatio > targetAspectRatio) {
            realHeight / 480f
        } else {
            realWidth / 640f
        }
        /*val scaleToFit = if (sourceAspectRatio > targetAspectRatio) {
            realWidth / 640f
        } else {
            realHeight / 480f
        }*/
        val transformationMatrix = Matrix().apply {
            setTranslate(-319.5f, -239.5f)
            postRotate(-imageInfo.rotationDegrees.toFloat())
            postScale(scaleToFill, scaleToFill)
        }
        val translateMatrix = Matrix().apply {
            if (imageInfo.rotationDegrees == 90 || imageInfo.rotationDegrees == 270) {
                setTranslate(239.5f * (realHeight / 480f), 319.5f * (realWidth / 640f))
            } else {
                setTranslate(319.5f * (realWidth / 640f), 239.5f * (realHeight / 480f))
            }
        }

        transformationMatrix.mapPoints(mappedPoints, originalPoints)
        translateMatrix.mapPoints(mappedPoints)

        val uPlaneXScale = sourceWidth / uPlane.rowStride
        val uPlaneYScale = sourceHeight / (uPlane.buffer.remaining() / uPlane.rowStride)
        val vPlaneXScale = sourceWidth / vPlane.rowStride
        val vPlaneYScale = sourceHeight / (vPlane.buffer.remaining() / vPlane.rowStride)

        val backBuffer = cameraBuffers.getBackBuffer()
        for (yPos in 0 until 480) {
            for (xPos in 0 until 640) {

                val targetInlinePos = (yPos * 640 + xPos) * 2

                val pX = mappedPoints[targetInlinePos].toInt()
                val pY = mappedPoints[targetInlinePos + 1].toInt()
                val y = yBuffer.get(pY * sourceWidth + pX)

                backBuffer[targetInlinePos] = y
                backBuffer[targetInlinePos + 1] = if (xPos % 2 == 0) {
                    // Divide and multiply by pixel stride to ensure that we are reading from aligned data
                    uPlane.buffer.get((pY / uPlaneYScale * sourceWidth / uPlaneXScale) + (pX / uPlaneXScale / uPlane.pixelStride * uPlane.pixelStride))
                } else {
                    vPlane.buffer.get((pY / vPlaneYScale * sourceWidth / vPlaneXScale) + (pX / vPlaneXScale / vPlane.pixelStride * vPlane.pixelStride))
                }
            }
        }

        cameraBuffers.swapBuffers()
    }

    private fun initializeOriginalPoints() {
        for (y in 0 until 480) {
            for (x in 0 until 640) {
                val inlinePos = (y * 640 + x) * 2
                originalPoints[inlinePos] = x.toFloat()
                originalPoints[inlinePos + 1] = y.toFloat()
            }
        }
    }

    fun destroy() {
        currentCameraProvider?.unbindAll()
        currentCameraProvider = null
        executor.shutdownNow()
    }
}