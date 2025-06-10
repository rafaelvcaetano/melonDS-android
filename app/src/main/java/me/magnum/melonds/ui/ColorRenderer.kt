package me.magnum.melonds.ui

import android.graphics.Color
import android.opengl.GLES30
import android.opengl.GLSurfaceView
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

/**
 * Simple renderer that clears the screen with a solid color.
 * Used as a placeholder for the top screen output on external displays.
 */
class ColorRenderer(initialColor: Int) : GLSurfaceView.Renderer {
    var color: Int = initialColor
        set(value) {
            field = value
        }

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        // nothing else to initialize
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        GLES30.glViewport(0, 0, width, height)
    }

    override fun onDrawFrame(gl: GL10?) {
        val r = Color.red(color) / 255f
        val g = Color.green(color) / 255f
        val b = Color.blue(color) / 255f
        GLES30.glClearColor(r, g, b, 1f)
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT)
    }
}
