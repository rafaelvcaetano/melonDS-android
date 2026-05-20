package me.magnum.melonds.ui.emulator.render

import android.hardware.HardwareBuffer
import android.hardware.SyncFence
import android.opengl.GLES30
import android.os.Build
import android.util.Log
import android.view.Surface
import android.view.SurfaceControl
import android.view.SurfaceView
import androidx.annotation.RequiresApi
import me.magnum.melonds.domain.model.render.PresentFrameWrapper
import java.time.Duration
import kotlin.time.Duration.Companion.milliseconds

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
class SurfaceFrontRenderer : SurfaceRenderer() {

    companion object {
        private const val TAG = "SurfaceFrontRenderer"
        private const val BUFFER_COUNT = 2
    }

    private enum class SurfaceState {
        UNINITIALIZED,
        DIRTY,
        READY,
    }

    private class RenderBuffer(
        var hardwareBuffer: HardwareBuffer? = null,
        var eglImage: Long = 0L,
        var fbo: Int = 0,
        var texture: Int = 0,
        var presentFence: SyncFence? = null,
    )

    private val renderBuffers = Array(BUFFER_COUNT) { RenderBuffer() }
    private var currentBufferIndex = 0
    private var surfaceControl: SurfaceControl? = null
    private var surfaceState = SurfaceState.UNINITIALIZED
    private var currentWidth: Int = 0
    private var currentHeight: Int = 0

    override fun doFrame(
        glContext: GlContext,
        surfaceView: SurfaceView,
        surface: Surface?,
        surfaceWidth: Int,
        surfaceHeight: Int,
        renderer: EmulatorRenderer?,
        presentFrameWrapper: PresentFrameWrapper,
    ) {
        if (surface == null) {
            releaseGlResources(glContext)
            return
        }

        if (surfaceWidth <= 0 || surfaceHeight <= 0) return

        glContext.useWithoutSurface()

        if (renderBuffers[0].hardwareBuffer == null || surfaceWidth != currentWidth || surfaceHeight != currentHeight) {
            releaseGlResources(glContext)
            if (!initializeResources(glContext, surfaceView, surfaceWidth, surfaceHeight)) {
                return
            }
            surfaceState = SurfaceState.UNINITIALIZED
        }

        if (surfaceState == SurfaceState.UNINITIALIZED && renderer != null) {
            renderer.onSurfaceCreated()
            surfaceState = SurfaceState.DIRTY
        }

        if (surfaceState == SurfaceState.DIRTY && renderer != null) {
            renderer.onSurfaceChanged(surfaceWidth, surfaceHeight)
            surfaceState = SurfaceState.READY
        }

        // Swap to the back buffer (the one not currently being displayed)
        currentBufferIndex = (currentBufferIndex + 1) % BUFFER_COUNT
        val buffer = renderBuffers[currentBufferIndex]

        // Bind FBO and attach texture (same pattern as AndroidX FrameBuffer.makeCurrent())
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, buffer.fbo)
        GLES30.glFramebufferTexture2D(
            GLES30.GL_FRAMEBUFFER,
            GLES30.GL_COLOR_ATTACHMENT0,
            GLES30.GL_TEXTURE_2D,
            buffer.texture,
            0
        )
        GLES30.glViewport(0, 0, currentWidth, currentHeight)

        if (buffer.presentFence?.isValid == true) {
            buffer.presentFence?.apply {
                await(Duration.ofMillis(100))
                close()
            }
        }

        renderer?.drawFrame(presentFrameWrapper)

        // Create sync fence so the compositor waits for GPU completion
        val syncFence = SyncFenceFactory.createNativeSyncFence()

        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)

        // Present via SurfaceControl transaction with fence
        val transaction = SurfaceControl.Transaction()
            .setVisibility(surfaceControl!!, true)
            .setBufferTransform(surfaceControl!!, SurfaceControl.BUFFER_TRANSFORM_MIRROR_VERTICAL)

        if (syncFence != null && syncFence.isValid) {
            transaction.setBuffer(surfaceControl!!, buffer.hardwareBuffer!!, syncFence) {
                buffer.presentFence = it
            }
        } else {
            transaction.setBuffer(surfaceControl!!, buffer.hardwareBuffer!!)
        }

        transaction.apply()
        syncFence?.close()
    }

    override fun stop(glContext: GlContext) {
        releaseGlResources(glContext)
        surfaceState = SurfaceState.UNINITIALIZED
    }

    private fun initializeResources(glContext: GlContext, surfaceView: SurfaceView, width: Int, height: Int): Boolean {
        currentWidth = width
        currentHeight = height

        try {
            val usageFlags = HardwareBuffer.USAGE_GPU_SAMPLED_IMAGE or
                    HardwareBuffer.USAGE_GPU_COLOR_OUTPUT or
                    HardwareBuffer.USAGE_COMPOSER_OVERLAY or
                    HardwareBuffer.USAGE_FRONT_BUFFER

            for (i in 0 until BUFFER_COUNT) {
                val rb = renderBuffers[i]

                rb.hardwareBuffer = HardwareBuffer.create(width, height, HardwareBuffer.RGBA_8888, 1, usageFlags)

                rb.eglImage = glContext.createEglImageFromHardwareBuffer(rb.hardwareBuffer!!)
                if (rb.eglImage == 0L) {
                    throw GlContext.GlContextException("Failed to create EGLImage from HardwareBuffer (buffer $i)")
                }

                // Create texture and bind EGLImage (same approach as AndroidX FrameBuffer)
                val texArray = IntArray(1)
                GLES30.glGenTextures(1, texArray, 0)
                rb.texture = texArray[0]

                GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, rb.texture)
                nativeGlEGLImageTargetTexture2DOES(rb.eglImage)
                GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, 0)

                // Create FBO
                val fboArray = IntArray(1)
                GLES30.glGenFramebuffers(1, fboArray, 0)
                rb.fbo = fboArray[0]

                // Verify FBO completeness
                GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, rb.fbo)
                GLES30.glFramebufferTexture2D(
                    GLES30.GL_FRAMEBUFFER,
                    GLES30.GL_COLOR_ATTACHMENT0,
                    GLES30.GL_TEXTURE_2D,
                    rb.texture,
                    0
                )

                val status = GLES30.glCheckFramebufferStatus(GLES30.GL_FRAMEBUFFER)
                if (status != GLES30.GL_FRAMEBUFFER_COMPLETE) {
                    throw GlContext.GlContextException("FBO incomplete (buffer $i): status=0x${status.toString(16)}")
                }
            }

            GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)

            // Create child SurfaceControl
            surfaceControl = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                SurfaceControl.Builder()
                    .setName("FrontBufferLayer")
                    .setParent(surfaceView.surfaceControl)
                    .setBufferSize(width, height)
                    .build()
            } else {
                SurfaceControl.Builder()
                    .setName("FrontBufferLayer")
                    .setBufferSize(width, height)
                    .build()
            }

            SurfaceControl.Transaction()
                .setVisibility(surfaceControl!!, true)
                .apply()

            currentBufferIndex = 0

            return true
        } catch (e: Exception) {
            Log.w(TAG, "Failed to initialize front-buffer resources", e)
            releaseGlResources(glContext)
            return false
        }
    }

    private fun releaseGlResources(glContext: GlContext) {
        for (rb in renderBuffers) {
            if (rb.fbo != 0) {
                GLES30.glDeleteFramebuffers(1, intArrayOf(rb.fbo), 0)
                rb.fbo = 0
            }
            if (rb.texture != 0) {
                GLES30.glDeleteTextures(1, intArrayOf(rb.texture), 0)
                rb.texture = 0
            }
            if (rb.eglImage != 0L) {
                glContext.destroyEglImage(rb.eglImage)
                rb.eglImage = 0L
            }
            rb.hardwareBuffer?.close()
            rb.hardwareBuffer = null
        }

        surfaceControl?.let {
            SurfaceControl.Transaction()
                .setVisibility(it, false)
                .apply()
            it.release()
        }
        surfaceControl = null

        currentWidth = 0
        currentHeight = 0
    }

    private external fun nativeGlEGLImageTargetTexture2DOES(eglImage: Long)
}
