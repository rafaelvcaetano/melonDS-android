package me.magnum.melonds.ui

import android.opengl.GLES30
import android.opengl.GLSurfaceView
import me.magnum.melonds.domain.model.render.FrameRenderEvent
import me.magnum.melonds.ui.ExternalRenderer
import me.magnum.melonds.domain.model.Rect
import me.magnum.melonds.common.opengl.Shader
import me.magnum.melonds.common.opengl.ShaderFactory
import me.magnum.melonds.common.opengl.ShaderProgramSource
import me.magnum.melonds.common.opengl.VideoFilterShaderProvider
import me.magnum.melonds.domain.model.VideoFiltering
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
 * and [ExternalRenderer] to receive frame data from the emulator.
 *
 * The layout of the screens (their positions and sizes) can be updated dynamically.
 *
 * @property topScreen The [Rect] defining the position and size of the top screen.
 *                     Can be null if the top screen is not to be rendered.
 * @property bottomScreen The [Rect] defining the position and size of the bottom screen.
 *                        Can be null if the bottom screen is not to be rendered.
 * @property layoutWidth The total width of the layout area where the screens are rendered.
 * @property layoutHeight The total height of the layout area where the screens are rendered.
 * @property topAlpha The alpha (transparency) value for the top screen, ranging from 0.0 (fully transparent) to 1.0 (fully opaque).
 * @property bottomAlpha The alpha (transparency) value for the bottom screen, ranging from 0.0 (fully transparent) to 1.0 (fully opaque).
 * @property topOnTop A boolean indicating whether the top screen should be rendered on top of the bottom screen if they overlap.
 * @property bottomOnTop A boolean indicating whether the bottom screen should be rendered on top of the top screen if they overlap.
 */
class ExternalLayoutRender(
    private var topScreen: Rect?,
    private var bottomScreen: Rect?,
    private var layoutWidth: Int,
    private var layoutHeight: Int,
    private var topAlpha: Float = 1f,
    private var bottomAlpha: Float = 1f,
    private var topOnTop: Boolean = false,
    private var bottomOnTop: Boolean = false,
) : GLSurfaceView.Renderer, ExternalRenderer {

    companion object {
        private const val TOTAL_SCREEN_HEIGHT = 384
    }

    private lateinit var shader: Shader

    private var posTop: FloatBuffer? = null
    private var posBottom: FloatBuffer? = null

    private lateinit var uvTop: FloatBuffer
    private lateinit var uvBottom: FloatBuffer

    private var nextRenderEvent: FrameRenderEvent? = null
    private var videoFiltering: VideoFiltering = VideoFiltering.NONE

    fun updateLayout(
        top: Rect?,
        bottom: Rect?,
        width: Int,
        height: Int,
        topA: Float,
        bottomA: Float,
        topOnT: Boolean,
        bottomOnT: Boolean,
    ) {
        topScreen = top
        bottomScreen = bottom
        layoutWidth = width
        layoutHeight = height
        topAlpha = topA
        bottomAlpha = bottomA
        topOnTop = topOnT
        bottomOnTop = bottomOnT
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
        shader = ShaderFactory.createShaderProgram(
            VideoFilterShaderProvider.getShaderSource(videoFiltering)
        )
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
            GLES30.glEnable(GLES30.GL_BLEND)
            GLES30.glBlendColor(0f, 0f, 0f, topAlpha)
            GLES30.glBlendFunc(GLES30.GL_CONSTANT_ALPHA, GLES30.GL_ONE_MINUS_CONSTANT_ALPHA)
            GLES30.glDrawArrays(GLES30.GL_TRIANGLES, 0, 6)
            GLES30.glDisable(GLES30.GL_BLEND)
        }
        posBottom?.let { buf ->
            buf.position(0)
            uvBottom.position(0)
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, textureId)
            GLES30.glVertexAttribPointer(shader.attribPos, 2, GLES30.GL_FLOAT, false, 0, buf)
            GLES30.glVertexAttribPointer(shader.attribUv, 2, GLES30.GL_FLOAT, false, 0, uvBottom)
            GLES30.glUniform1i(shader.uniformTex, 0)
            GLES30.glEnable(GLES30.GL_BLEND)
            GLES30.glBlendColor(0f, 0f, 0f, bottomAlpha)
            GLES30.glBlendFunc(GLES30.GL_CONSTANT_ALPHA, GLES30.GL_ONE_MINUS_CONSTANT_ALPHA)
            GLES30.glDrawArrays(GLES30.GL_TRIANGLES, 0, 6)
            GLES30.glDisable(GLES30.GL_BLEND)
        }
    }

    override fun prepareNextFrame(frameRenderEvent: FrameRenderEvent) {
        nextRenderEvent = frameRenderEvent
    }

    /**
     * Updates the video filtering setting used by the renderer.
     *
     * If the shader has already been initialized, it will be deleted and a new
     * shader will be created with the updated filtering.
     *
     * @param videoFiltering The new [VideoFiltering] setting to apply.
     */
    override fun updateVideoFiltering(videoFiltering: VideoFiltering) {
        this.videoFiltering = videoFiltering
        if (this::shader.isInitialized) {
            shader.delete()
            shader = ShaderFactory.createShaderProgram(
                VideoFilterShaderProvider.getShaderSource(videoFiltering)
            )
        }
    }

}