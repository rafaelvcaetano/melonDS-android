package me.magnum.melonds.ui

import android.graphics.Color
import android.opengl.GLES30
import android.opengl.GLSurfaceView
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

/**
 * Simple renderer that clears the screen with a solid color.
 * Used as a placeholder for the top screen output on external displays.
 *
 * @param initialColor The initial color to clear the screen with.
 */
class ColorRenderer(initialColor: Int) : GLSurfaceView.Renderer {
    var color: Int = initialColor

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        // nothing else to initialize
    }

    /**
     * Called when the surface changes size.
     * Sets the OpenGL viewport to the new width and height.
     *
     * @param gl The GL interface. Unused in this implementation.
     * @param width The new width of the surface.
     * @param height The new height of the surface.
     */
    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        GLES30.glViewport(0, 0, width, height)
    }

    /**
     * Clears the screen with the current [color].
     *
     * Called to draw the current frame. This method is responsible for drawing the content of the view.
     * It is called continuously by the rendering thread, unless the render mode is set to
     * `RENDERMODE_WHEN_DIRTY`.
     *
     * @param gl The GL interface.
     */
    override fun onDrawFrame(gl: GL10?) {
        val r = Color.red(color) / 255f
        val g = Color.green(color) / 255f
        val b = Color.blue(color) / 255f
        GLES30.glClearColor(r, g, b, 1f)
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT)
    }
}