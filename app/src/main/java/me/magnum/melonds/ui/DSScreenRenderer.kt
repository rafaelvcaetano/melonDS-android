package me.magnum.melonds.ui

import android.opengl.GLES30
import android.opengl.GLSurfaceView
import me.magnum.melonds.domain.model.DsScreen
import me.magnum.melonds.common.opengl.Shader
import me.magnum.melonds.common.opengl.ShaderFactory
import me.magnum.melonds.common.opengl.ShaderProgramSource
import me.magnum.melonds.domain.model.render.FrameRenderEvent
import me.magnum.melonds.ui.emulator.FrameRenderEventConsumer
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

/**
 * Renderer that draws one of the DS screens using the texture provided
 * by the emulator.
 */
class DSScreenRenderer(
    private val screen: DsScreen
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
        val uvs = if (screen == DsScreen.TOP) {
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
