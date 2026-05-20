package me.magnum.melonds.ui.emulator.render

import android.opengl.EGLSurface
import android.opengl.GLES30
import android.view.Surface
import android.view.SurfaceView
import me.magnum.melonds.domain.model.render.PresentFrameWrapper

class SurfaceBackRenderer : SurfaceRenderer() {

    private enum class SurfaceState {
        UNINITIALIZED,
        DIRTY,
        READY,
    }

    private var windowSurface: EGLSurface? = null
    private var surfaceState = SurfaceState.UNINITIALIZED
    private var lastWidth = 0
    private var lastHeight = 0

    override fun doFrame(
        glContext: GlContext,
        surfaceView: SurfaceView,
        surface: Surface?,
        surfaceWidth: Int,
        surfaceHeight: Int,
        renderer: EmulatorRenderer?,
        presentFrameWrapper: PresentFrameWrapper,
    ) {
        if (windowSurface == null) {
            val currentSurface = surface ?: return
            windowSurface = glContext.createWindowSurface(currentSurface)
        } else if (surface == null) {
            // Surface was destroyed
            windowSurface?.let {
                glContext.destroyWindowSurface(it)
                windowSurface = null
            }
            return
        }

        glContext.use(windowSurface!!)
        GLES30.glViewport(0, 0, surfaceView.width, surfaceView.height)

        if (surfaceState == SurfaceState.UNINITIALIZED && renderer != null) {
            renderer.onSurfaceCreated()
            surfaceState = SurfaceState.DIRTY
        }

        if (surfaceWidth != lastWidth || surfaceHeight != lastHeight) {
            surfaceState = SurfaceState.DIRTY
            lastWidth = surfaceWidth
            lastHeight = surfaceHeight
        }

        if (surfaceState == SurfaceState.DIRTY && renderer != null) {
            renderer.onSurfaceChanged(surfaceWidth, surfaceHeight)
            surfaceState = SurfaceState.READY
        }

        renderer?.drawFrame(presentFrameWrapper)
        glContext.swapBuffers(windowSurface!!)
    }

    override fun stop(glContext: GlContext) {
        windowSurface?.let {
            glContext.destroyWindowSurface(it)
            windowSurface = null
        }
        surfaceState = SurfaceState.UNINITIALIZED
        lastWidth = 0
        lastHeight = 0
    }
}
