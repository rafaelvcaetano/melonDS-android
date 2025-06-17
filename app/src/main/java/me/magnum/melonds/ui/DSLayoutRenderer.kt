package me.magnum.melonds.ui

import android.opengl.GLES30
import android.opengl.GLSurfaceView
import me.magnum.melonds.domain.model.Rect
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
 * Renderer that draws both DS screens using layout coordinates and the
 * texture provided by the emulator.
 */
class DSLayoutRenderer(
    private var topScreen: Rect?,
    private var bottomScreen: Rect?,
    private var layoutWidth: Int,
    private var layoutHeight: Int,
    private var topAlpha: Float = 1f,
    private var bottomAlpha: Float = 1f,
    private var topOnTop: Boolean = false,
    private var bottomOnTop: Boolean = false,
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

    fun updateLayout(
        top: Rect?,
        bottom: Rect?,
        width: Int,
        height: Int,
        topAlpha: Float = this.topAlpha,
        bottomAlpha: Float = this.bottomAlpha,
        topOnTop: Boolean = this.topOnTop,
        bottomOnTop: Boolean = this.bottomOnTop,
    ) {
        topScreen = top
        bottomScreen = bottom
        layoutWidth = width
        layoutHeight = height
        this.topAlpha = topAlpha
        this.bottomAlpha = bottomAlpha
        this.topOnTop = topOnTop
        this.bottomOnTop = bottomOnTop
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
        val screens = listOfNotNull(
            posTop?.let { Triple(it, uvTop, Pair(topAlpha, topOnTop)) },
            posBottom?.let { Triple(it, uvBottom, Pair(bottomAlpha, bottomOnTop)) },
        ).sortedBy { if (it.third.second) 1 else 0 }

        screens.forEach { (buf, uv, info) ->
            val alpha = info.first
            buf.position(0)
            uv.position(0)
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, textureId)
            GLES30.glVertexAttribPointer(shader.attribPos, 2, GLES30.GL_FLOAT, false, 0, buf)
            GLES30.glVertexAttribPointer(shader.attribUv, 2, GLES30.GL_FLOAT, false, 0, uv)
            GLES30.glUniform1i(shader.uniformTex, 0)
            GLES30.glEnable(GLES30.GL_BLEND)
            GLES30.glBlendColor(0f, 0f, 0f, alpha)
            GLES30.glBlendFunc(GLES30.GL_CONSTANT_ALPHA, GLES30.GL_ONE_MINUS_CONSTANT_ALPHA)
            GLES30.glDrawArrays(GLES30.GL_TRIANGLES, 0, 6)
            GLES30.glDisable(GLES30.GL_BLEND)
        }
    }

    override fun prepareNextFrame(frameRenderEvent: FrameRenderEvent) {
        nextRenderEvent = frameRenderEvent
    }
}
