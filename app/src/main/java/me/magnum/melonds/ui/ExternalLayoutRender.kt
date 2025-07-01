package me.magnum.melonds.ui

import android.opengl.GLES30
import android.opengl.GLSurfaceView
import me.magnum.melonds.domain.model.render.FrameRenderEvent
import me.magnum.melonds.ui.emulator.FrameRenderEventConsumer
import me.magnum.melonds.domain.model.Rect
import me.magnum.melonds.common.opengl.Shader
import me.magnum.melonds.common.opengl.ShaderFactory
import me.magnum.melonds.common.opengl.ShaderProgramSource
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

/**
 * Renders the emulator's top and bottom screens onto a [GLSurfaceView]
 * according to the provided layout information.
 *
 * This class implements [GLSurfaceView.Renderer] to handle OpenGL rendering
 * and [FrameRenderEventConsumer] to receive frame data from the emulator.
 *
 * The layout of the screens (their positions and sizes) can be updated dynamically.
 *
 * @property topScreen The [Rect] defining the position and size of the top screen.
 *                     Can be null if the top screen is not to be rendered.
 * @property bottomScreen The [Rect] defining the position and size of the bottom screen.
 *                        Can be null if the bottom screen is not to be rendered.
 * @property layoutWidth The total width of the layout area where the screens are rendered.
 * @property layoutHeight The total height of the layout area where the screens are rendered.
 */
class ExternalLayoutRender(
    private var topScreen: Rect?,
    private var bottomScreen: Rect?,
    private var layoutWidth: Int,
    private var layoutHeight: Int,
) : GLSurfaceView.Renderer, FrameRenderEventConsumer {

    companion object {
        private const val TOTAL_SCREEN_HEIGHT = 384
    }

    private lateinit var shader: Shader

    private var posTop: FloatBuffer? = null
    private var posBottom: FloatBuffer? = null

    private lateinit var uvTop: FloatBuffer
    private lateinit var uvBottom: FloatBuffer

    private var nextRenderEvent: FrameRenderEvent? = null

    fun updateLayout(top: Rect?, bottom: Rect?, width: Int, height: Int){
        topScreen = top
        bottomScreen = bottom
        layoutWidth = width
        layoutHeight = height
        updateBuffers()
    }

    private fun rectToBuffer(rect: Rect): FloatBuffer {
        val left = rect.x / layoutWidth.toFloat() * 2f - 1f
        val right = (rect.x + rect.width) / layoutWidth.toFloat() * 2f - 1f
        val top = 1f - rect.y / layoutHeight.toFloat() * 2f
        val bottom = 1f - (rect.y + rect.height) / layoutHeight.toFloat() * 2f
        val coords = floatArrayOf(
            left, bottom,
            left, top,
            right, top,
            left, bottom,
            right, top,
            right, bottom,
        )
        return ByteBuffer.allocateDirect(coords.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .put(coords)
    }

    private fun updateBuffers() {
        posTop = topScreen?.let { rectToBuffer(it) }
        posBottom = bottomScreen?.let { rectToBuffer(it) }
    }

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        shader = ShaderFactory.createShaderProgram(ShaderProgramSource.NoFilterShader)
        val lineRelativeSize = 1f / (TOTAL_SCREEN_HEIGHT + 1).toFloat()
        val topUvs = floatArrayOf(
            0f, 0.5f - lineRelativeSize,
            0f, 0f,
            1f, 0f,
            0f, 0.5f - lineRelativeSize,
            1f, 0f,
            1f, 0.5f - lineRelativeSize,
        )
        val bottomUvs = floatArrayOf(
            0f, 1f,
            0f, 0.5f + lineRelativeSize,
            1f, 0.5f + lineRelativeSize,
            0f, 1f,
            1f, 0.5f + lineRelativeSize,
            1f, 1f,
        )
        uvTop = ByteBuffer.allocateDirect(topUvs.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .put(topUvs)
        uvBottom = ByteBuffer.allocateDirect(bottomUvs.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .put(bottomUvs)
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        GLES30.glViewport(0, 0, width, height)
        updateBuffers()
    }

    override fun onDrawFrame(gl: GL10?) {
        val textureId = nextRenderEvent?.textureId ?: return

        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT)
        shader.use()

        posTop?.let { buf ->
            buf.position(0)
            uvTop.position(0)
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, textureId)
            GLES30.glVertexAttribPointer(shader.attribPos, 2, GLES30.GL_FLOAT, false, 0, buf)
            GLES30.glVertexAttribPointer(shader.attribUv, 2, GLES30.GL_FLOAT, false, 0, uvTop)
            GLES30.glUniform1i(shader.uniformTex, 0)
            GLES30.glDrawArrays(GLES30.GL_TRIANGLES, 0, 6)
        }
        posBottom?.let { buf ->
            buf.position(0)
            uvBottom.position(0)
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, textureId)
            GLES30.glVertexAttribPointer(shader.attribPos, 2, GLES30.GL_FLOAT, false, 0, buf)
            GLES30.glVertexAttribPointer(shader.attribUv, 2, GLES30.GL_FLOAT, false, 0, uvBottom)
            GLES30.glUniform1i(shader.uniformTex, 0)
            GLES30.glDrawArrays(GLES30.GL_TRIANGLES, 0, 6)
        }
    }

    override fun prepareNextFrame(frameRenderEvent: FrameRenderEvent) {
        nextRenderEvent = frameRenderEvent
    }
}