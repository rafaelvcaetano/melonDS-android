package me.magnum.melonds.ui

import android.opengl.GLES30
import android.opengl.GLSurfaceView
import me.magnum.melonds.common.opengl.Shader
import me.magnum.melonds.common.opengl.ShaderFactory
import me.magnum.melonds.common.opengl.ShaderProgramSource
import java.nio.ByteBuffer
import java.nio.ByteOrder
import me.magnum.melonds.domain.model.render.FrameRenderEvent
import me.magnum.melonds.ui.emulator.FrameRenderEventConsumer
import java.nio.FloatBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import me.magnum.melonds.domain.model.DsExternalScreen

/**
 * Renders a single DS screen (top or bottom) to a GLSurfaceView.
 *
 * This class implements [GLSurfaceView.Renderer] to handle OpenGL rendering callbacks and
 * [FrameRenderEventConsumer] to receive frame data from the emulator.
 *
 * It uses a simple shader to draw a texture containing the screen content onto a quad
 * that fills the GLSurfaceView. The texture coordinates are adjusted based on whether
 * it's rendering the top or bottom screen, as both screens are typically rendered
 * into a single larger texture by the emulator.
 *
 * @property screen Specifies which DS screen (TOP or BOTTOM) this renderer is responsible for.
 */
class ExternalScreenRender(
    private val screen: DsExternalScreen
) : GLSurfaceView.Renderer, FrameRenderEventConsumer {

    companion object {
        private const val TOTAL_SCREEN_HEIGHT = 384
    }

    private lateinit var shader: Shader
    private lateinit var posBuffer: FloatBuffer
    private lateinit var uvBuffer: FloatBuffer
    private var nextRenderEvent: FrameRenderEvent? = null

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        shader = ShaderFactory.createShaderProgram(ShaderProgramSource.NoFilterShader)

        val coords = floatArrayOf(
            -1f, -1f,
            -1f, 1f,
            1f, 1f,
            -1f, -1f,
            1f, 1f,
            1f, -1f
        )

        val lineRelativeSize = 1f / (TOTAL_SCREEN_HEIGHT + 1).toFloat()
        val uvs = if (screen == DsExternalScreen.TOP) {
            floatArrayOf(
                0f, 0.5f - lineRelativeSize,
                0f, 0f,
                1f, 0f,
                0f, 0.5f - lineRelativeSize,
                1f, 0f,
                1f, 0.5f - lineRelativeSize
            )
        } else {
            floatArrayOf(
                0f, 1f,
                0f, 0.5f + lineRelativeSize,
                1f, 0.5f + lineRelativeSize,
                0f, 1f,
                1f, 0.5f + lineRelativeSize,
                1f, 1f
            )
        }

        posBuffer = ByteBuffer.allocateDirect(coords.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .put(coords)
        uvBuffer = ByteBuffer.allocateDirect(uvs.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .put(uvs)
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        GLES30.glViewport(0, 0, width, height)
    }

    override fun onDrawFrame(gl: GL10?) {
        val textureId = nextRenderEvent?.textureId ?: return

        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT)
        shader.use()
        posBuffer.position(0)
        uvBuffer.position(0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, textureId)
        GLES30.glVertexAttribPointer(shader.attribPos, 2, GLES30.GL_FLOAT, false, 0, posBuffer)
        GLES30.glVertexAttribPointer(shader.attribUv, 2, GLES30.GL_FLOAT, false, 0, uvBuffer)
        GLES30.glUniform1i(shader.uniformTex, 0)
        GLES30.glDrawArrays(GLES30.GL_TRIANGLES, 0, posBuffer.capacity() / 2)
    }

    override fun prepareNextFrame(frameRenderEvent: FrameRenderEvent) {
        nextRenderEvent = frameRenderEvent
    }
}