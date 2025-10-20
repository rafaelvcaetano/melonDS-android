package me.magnum.melonds.ui.emulator.render

import android.graphics.Color
import android.opengl.GLES30
import me.magnum.melonds.domain.model.render.PresentFrameWrapper
import me.magnum.melonds.ui.emulator.model.RuntimeRendererConfiguration

class ColorRenderer(private val color: Int) : EmulatorRenderer {

    override fun updateRendererConfiguration(newRendererConfiguration: RuntimeRendererConfiguration?) {
    }

    override fun setLeftRotationEnabled(enabled: Boolean) {
    }

    override fun onSurfaceCreated() {
    }

    override fun onSurfaceChanged(width: Int, height: Int) {
    }

    override fun drawFrame(presentFrameWrapper: PresentFrameWrapper) {
        val r = Color.red(color) / 255f
        val g = Color.green(color) / 255f
        val b = Color.blue(color) / 255f
        GLES30.glClearColor(r, g, b, 1f)
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT)
    }
}