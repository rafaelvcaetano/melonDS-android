package me.magnum.melonds.ui.emulator

import android.content.Context
import android.opengl.EGLSurface
import android.opengl.GLES30
import android.util.AttributeSet
import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView
import me.magnum.melonds.domain.model.render.PresentFrameWrapper
import me.magnum.melonds.ui.emulator.model.RuntimeRendererConfiguration
import me.magnum.melonds.ui.emulator.render.EmulatorRenderer
import me.magnum.melonds.ui.emulator.render.GlContext

class EmulatorSurfaceView(context: Context, attrs: AttributeSet? = null) : SurfaceView(context, attrs), SurfaceHolder.Callback {

    private val surfaceLock = Object()
    private var surfaceWidth = 0
    private var surfaceHeight = 0
    private var surfaceState = SurfaceState.UNINITIALIZED
    private var surface: Surface? = null
    private var windowSurface: EGLSurface? = null
    private var renderer: EmulatorRenderer? = null

    private enum class SurfaceState {
        UNINITIALIZED,
        DIRTY,
        READY;
    }

    init {
        holder.addCallback(this)
    }

    fun setRenderer(emulatorRenderer: EmulatorRenderer) {
        renderer = emulatorRenderer
    }

    fun updateRendererConfiguration(newRendererConfiguration: RuntimeRendererConfiguration?) {
        renderer?.updateRendererConfiguration(newRendererConfiguration)
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

    fun doFrame(glContext: GlContext, presentFrameWrapper: PresentFrameWrapper) {
        synchronized(surfaceLock) {
            if (windowSurface == null) {
                if (!setupWindowSurface(glContext)) {
                    return
                }
            } else if (surface == null) {
                // We had a surface, but it has been destroyed
                windowSurface?.let {
                    glContext.destroyWindowSurface(it)
                    windowSurface = null
                }
                return
            }

            glContext.use(windowSurface!!)
            GLES30.glViewport(0, 0, width, height)

            if (surfaceState == SurfaceState.UNINITIALIZED && renderer != null) {
                renderer?.onSurfaceCreated()
                surfaceState = SurfaceState.DIRTY
            }

            if (surfaceState == SurfaceState.DIRTY && renderer != null) {
                renderer?.onSurfaceChanged(surfaceWidth, surfaceHeight)
                surfaceState = SurfaceState.READY
            }

            renderer?.drawFrame(presentFrameWrapper)
            glContext.swapBuffers(windowSurface!!)
        }
    }

    private fun setupWindowSurface(glContext: GlContext): Boolean {
        val currentSurface = surface ?: return false
        windowSurface = glContext.createWindowSurface(currentSurface)
        return true
    }

    fun stop(glContext: GlContext) {
        synchronized(surfaceLock) {
            windowSurface?.let {
                glContext.destroyWindowSurface(it)
                windowSurface = null
            }
        }
    }
}