package me.magnum.melonds.ui.emulator

import android.content.Context
import android.opengl.EGLSurface
import android.opengl.GLES30
import android.util.AttributeSet
import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView
import me.magnum.melonds.MelonEmulator
import me.magnum.melonds.domain.model.render.PresentFrameWrapper
import me.magnum.melonds.ui.emulator.render.GlContext

class EmulatorSurfaceView(context: Context, attrs: AttributeSet? = null) : SurfaceView(context, attrs), SurfaceHolder.Callback {

    fun interface OnGlContextReady {
        fun onGlContextReady(glContext: Long)
    }

    private val surfaceLock = Object()
    private val presentFrameWrapper = PresentFrameWrapper()
    private var surfaceWidth = 0
    private var surfaceHeight = 0
    private var surfaceState = SurfaceState.UNINITIALIZED
    private var onGlContextReady: OnGlContextReady? = null
    private var surface: Surface? = null
    private var glContext: GlContext? = null
    private var windowSurface: EGLSurface? = null
    private var dsRenderer: DSRenderer? = null

    private enum class SurfaceState {
        UNINITIALIZED,
        DIRTY,
        READY;
    }

    init {
        holder.addCallback(this)
    }

    fun setRenderer(renderer: DSRenderer) {
        dsRenderer = renderer
    }

    fun setOnGlContextReadyListener(listener: OnGlContextReady) {
        onGlContextReady = listener
        synchronized(surfaceLock) {
            if (glContext != null) {
                onGlContextReady?.onGlContextReady(glContext!!.contextNativeHandle)
            }
        }
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        synchronized(surfaceLock) {
            surface = holder.surface
        }
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        synchronized(surfaceLock) {
            surfaceWidth = width
            surfaceHeight = height
            if (surfaceState == SurfaceState.READY) {
                surfaceState = SurfaceState.DIRTY
            }
        }
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        synchronized(surfaceLock) {
            surface = null
        }
    }

    fun doFrame() {
        synchronized(surfaceLock) {
            if (glContext == null) {
                glContext = GlContext()
                onGlContextReady?.onGlContextReady(glContext!!.contextNativeHandle)
            }

            if (windowSurface == null) {
                if (!setupWindowSurface()) {
                    return
                }
            } else if (surface == null) {
                // We had a surface, but it has been destroyed
                windowSurface?.let {
                    glContext?.destroyWindowSurface(it)
                    windowSurface = null
                }
                return
            }

            if (surfaceState == SurfaceState.UNINITIALIZED && dsRenderer != null) {
                dsRenderer?.onSurfaceCreated()
                surfaceState = SurfaceState.DIRTY
            }

            if (surfaceState == SurfaceState.DIRTY && dsRenderer != null) {
                dsRenderer?.onSurfaceChanged(surfaceWidth, surfaceHeight)
                surfaceState = SurfaceState.READY
            }

            MelonEmulator.presentFrame { isValidFrame, frameTextureId, renderFenceHandle ->
                presentFrameWrapper.apply {
                    this.isValidFrame = isValidFrame
                    this.textureId = frameTextureId
                    this.renderFenceHandle = renderFenceHandle
                }
                dsRenderer?.drawFrame(presentFrameWrapper)

                if (isValidFrame) {
                    GLES30.glFenceSync(GLES30.GL_SYNC_GPU_COMMANDS_COMPLETE, 0)
                } else {
                    0L
                }
            }

            glContext?.swapBuffers(windowSurface!!)
        }
    }

    private fun setupWindowSurface(): Boolean {
        val currentSurface = surface ?: return false
        val windowSurface = glContext?.createWindowSurface(currentSurface) ?: return false

        glContext?.use(windowSurface)
        this.windowSurface = windowSurface
        return true
    }

    fun stop() {
        synchronized(surfaceLock) {
            windowSurface?.let {
                glContext?.destroyWindowSurface(it)
                windowSurface = null
            }

            glContext?.release()
            glContext?.destroy()
        }
    }
}