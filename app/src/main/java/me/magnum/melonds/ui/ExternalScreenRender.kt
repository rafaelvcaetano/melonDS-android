package me.magnum.melonds.ui

import android.opengl.GLES30
import android.opengl.GLSurfaceView
import me.magnum.melonds.common.opengl.Shader
import me.magnum.melonds.common.opengl.ShaderFactory
import me.magnum.melonds.common.opengl.VideoFilterShaderProvider
import me.magnum.melonds.domain.model.DsExternalScreen
import me.magnum.melonds.domain.model.VideoFiltering
import me.magnum.melonds.domain.model.render.FrameRenderEvent
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

/**
 * Renders a single DS screen (top or bottom) to a GLSurfaceView.
 *
 * This class implements [GLSurfaceView.Renderer] to handle OpenGL rendering callbacks and
 * [ExternalRenderer] to receive frame data from the emulator.
 *
 * It uses a simple shader to draw a texture containing the screen content onto a quad
 * that fills the GLSurfaceView. The texture coordinates are adjusted based on whether
 * it's rendering the top or bottom screen, as both screens are typically rendered
 * into a single larger texture by the emulator.
 *
 * @property screen Specifies which DS screen (TOP or BOTTOM) this renderer is responsible for.
 */
class ExternalScreenRender(
    private val screen: DsExternalScreen,
    private val rotateLeft: Boolean,
) : GLSurfaceView.Renderer, ExternalRenderer {

    companion object {
        private const val TOTAL_SCREEN_HEIGHT = 384
    }

    private lateinit var shader: Shader
    private var screensVbo = 0
    private var screensVao = 0

    private var nextRenderEvent: FrameRenderEvent? = null
    private var videoFiltering: VideoFiltering = VideoFiltering.NONE

    private var surfaceWidth = 0
    private var surfaceHeight = 0
    private var keepAspectRatio = false

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        shader = ShaderFactory.createShaderProgram(
            VideoFilterShaderProvider.getShaderSource(videoFiltering)
        )

        val buffers = IntArray(2)
        GLES30.glGenBuffers(1, buffers, 0)
        GLES30.glGenVertexArrays(1, buffers, 1)
        screensVbo = buffers[0]
        screensVao = buffers[1]

        val coords = floatArrayOf(
            -1f, -1f,
            -1f, 1f,
            1f, 1f,
            -1f, -1f,
            1f, 1f,
            1f, -1f
        )

        if (rotateLeft) {
            RdsRotation.rotateLeft(coords)
        }

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

        val vertexData = floatArrayOf(
            coords[0], coords[1],   uvs[0], uvs[1],
            coords[2], coords[3],   uvs[2], uvs[3],
            coords[4], coords[5],   uvs[4], uvs[5],
            coords[6], coords[7],   uvs[6], uvs[7],
            coords[8], coords[9],   uvs[8], uvs[9],
            coords[10], coords[11], uvs[10], uvs[11],
        )

        val vertexBufferSize = vertexData.size * Float.SIZE_BYTES
        val vertexBuffer = ByteBuffer.allocateDirect(vertexBufferSize)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .put(vertexData)
            .position(0)

        GLES30.glBindVertexArray(screensVao)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, screensVbo)
        GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, vertexBufferSize, vertexBuffer, GLES30.GL_STATIC_DRAW)
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        surfaceWidth = width
        surfaceHeight = height
        updateViewport()
    }

    override fun onDrawFrame(gl: GL10?) {
        val event = nextRenderEvent ?: return
        if (!event.isValidFrame) {
            return
        }

        GLES30.glWaitSync(event.renderFenceHandle, 0, GLES30.GL_TIMEOUT_IGNORED)
        val textureId = event.textureId

        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT)
        shader.use()
        GLES30.glDisableVertexAttribArray(shader.attribAlpha)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, textureId)
        GLES30.glBindVertexArray(screensVao)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, screensVbo)
        GLES30.glVertexAttribPointer(shader.attribPos, 2, GLES30.GL_FLOAT, false, 4 * Float.SIZE_BYTES, 0)
        GLES30.glVertexAttribPointer(shader.attribUv, 2, GLES30.GL_FLOAT, false, 4 * Float.SIZE_BYTES, 2 * Float.SIZE_BYTES)
        GLES30.glUniform1i(shader.uniformTex, 0)
        GLES30.glDrawArrays(GLES30.GL_TRIANGLES, 0, 6)
    }

    override fun prepareNextFrame(frameRenderEvent: FrameRenderEvent) {
        nextRenderEvent = if (frameRenderEvent.isValidFrame) {
            frameRenderEvent
        } else {
            null
        }
    }

    fun setKeepAspectRatio(keep: Boolean) {
        keepAspectRatio = keep
        updateViewport()
    }

    private fun updateViewport() {
        if (surfaceWidth == 0 || surfaceHeight == 0) return
        if (keepAspectRatio) {
            val dsRatio = 256f / 192f
            val viewRatio = surfaceWidth.toFloat() / surfaceHeight
            if (viewRatio > dsRatio) {
                val newWidth = (surfaceHeight * dsRatio).toInt()
                val x = (surfaceWidth - newWidth) / 2
                GLES30.glViewport(x, 0, newWidth, surfaceHeight)
            } else {
                val newHeight = (surfaceWidth / dsRatio).toInt()
                val y = (surfaceHeight - newHeight) / 2
                GLES30.glViewport(0, y, surfaceWidth, newHeight)
            }
        } else {
            GLES30.glViewport(0, 0, surfaceWidth, surfaceHeight)
        }
    }

    /**
     * Updates the video filtering setting for the renderer.
     *
     * This function changes the shader used for rendering to apply the specified
     * video filtering effect. If a shader is already initialized, it is deleted
     * and a new one is created based on the new filtering setting.
     *
     * @param videoFiltering The [VideoFiltering] mode to apply.
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