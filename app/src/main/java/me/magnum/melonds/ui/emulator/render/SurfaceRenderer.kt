package me.magnum.melonds.ui.emulator.render

import android.view.Surface
import android.view.SurfaceView
import me.magnum.melonds.domain.model.render.PresentFrameWrapper

abstract class SurfaceRenderer {

    abstract fun doFrame(
        glContext: GlContext,
        surfaceView: SurfaceView,
        surface: Surface?,
        surfaceWidth: Int,
        surfaceHeight: Int,
        renderer: EmulatorRenderer?,
        presentFrameWrapper: PresentFrameWrapper,
    )

    abstract fun stop(glContext: GlContext)
}
