package me.magnum.melonds.ui

import android.opengl.GLES30
import android.opengl.GLSurfaceView
import me.magnum.melonds.common.runtime.ScreenshotFrameBufferProvider
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
 * Simple renderer that draws both DS screens using coordinates provided by a layout.
 * It relies on the screenshot framebuffer updated by the native code.
 */
class DSLayoutRenderer(
    private val frameBufferProvider: ScreenshotFrameBufferProvider,
    private var topScreen: Rect?,
    private var bottomScreen: Rect?,
) : GLSurfaceView.Renderer {

    companion object {
        private const val SCREEN_WIDTH = 256
        private const val SCREEN_HEIGHT = 192
    }

    private lateinit var shader: Shader
    private val textureIds = IntArray(2)

    private var viewWidth = 0f
    private var viewHeight = 0f
    private var posTop: FloatBuffer? = null
    private var posBottom: FloatBuffer? = null
    private val uvBuffer: FloatBuffer = ByteBuffer.allocateDirect(6 * 2 * 4)
        .order(ByteOrder.nativeOrder())
        .asFloatBuffer()
        .apply {
            put(
                floatArrayOf(
                    0f, 1f,
                    0f, 0f,
                    1f, 0f,
                    0f, 1f,
                    1f, 0f,
                    1f, 1f,
                )
            )
        }

    fun updateRects(top: Rect?, bottom: Rect?) {
        topScreen = top
        bottomScreen = bottom
        updateBuffers()
    }

    private fun rectToBuffer(rect: Rect): FloatBuffer {
        val left = rect.x / viewWidth * 2f - 1f
        val right = (rect.x + rect.width) / viewWidth * 2f - 1f
        val top = (viewHeight - rect.y) / viewHeight * 2f - 1f
        val bottom = (viewHeight - (rect.y + rect.height)) / viewHeight * 2f - 1f
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
        GLES30.glGenTextures(2, textureIds, 0)
        for (id in textureIds) {
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, id)
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, shader.textureFiltering)
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, shader.textureFiltering)
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE)
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE)
            GLES30.glTexImage2D(
                GLES30.GL_TEXTURE_2D,
                0,
                GLES30.GL_RGBA,
                SCREEN_WIDTH,
                SCREEN_HEIGHT,
                0,
                GLES30.GL_RGBA,
                GLES30.GL_UNSIGNED_BYTE,
                null
            )
        }
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        viewWidth = width.toFloat()
        viewHeight = height.toFloat()
        GLES30.glViewport(0, 0, width, height)
        updateBuffers()
    }

    override fun onDrawFrame(gl: GL10?) {
        val buffer = frameBufferProvider.frameBuffer()
        val topSlice = buffer.duplicate().apply {
            limit(SCREEN_WIDTH * SCREEN_HEIGHT * 4)
        }
        val bottomSlice = buffer.duplicate().apply {
            position(SCREEN_WIDTH * SCREEN_HEIGHT * 4)
            limit(SCREEN_WIDTH * SCREEN_HEIGHT * 8)
        }
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, textureIds[0])
        GLES30.glTexSubImage2D(
            GLES30.GL_TEXTURE_2D,
            0,
            0,
            0,
            SCREEN_WIDTH,
            SCREEN_HEIGHT,
            GLES30.GL_RGBA,
            GLES30.GL_UNSIGNED_BYTE,
            topSlice
        )
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, textureIds[1])
        GLES30.glTexSubImage2D(
            GLES30.GL_TEXTURE_2D,
            0,
            0,
            0,
            SCREEN_WIDTH,
            SCREEN_HEIGHT,
            GLES30.GL_RGBA,
            GLES30.GL_UNSIGNED_BYTE,
            bottomSlice
        )

        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT)
        shader.use()

        uvBuffer.position(0)
        posTop?.let { buf ->
            buf.position(0)
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, textureIds[0])
            GLES30.glVertexAttribPointer(shader.attribPos, 2, GLES30.GL_FLOAT, false, 0, buf)
            GLES30.glVertexAttribPointer(shader.attribUv, 2, GLES30.GL_FLOAT, false, 0, uvBuffer)
            GLES30.glUniform1i(shader.uniformTex, 0)
            GLES30.glDrawArrays(GLES30.GL_TRIANGLES, 0, 6)
        }
        posBottom?.let { buf ->
            buf.position(0)
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, textureIds[1])
            GLES30.glVertexAttribPointer(shader.attribPos, 2, GLES30.GL_FLOAT, false, 0, buf)
            GLES30.glVertexAttribPointer(shader.attribUv, 2, GLES30.GL_FLOAT, false, 0, uvBuffer)
            GLES30.glUniform1i(shader.uniformTex, 0)
            GLES30.glDrawArrays(GLES30.GL_TRIANGLES, 0, 6)
        }
    }
}
