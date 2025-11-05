package me.magnum.melonds.ui.emulator.render

import android.opengl.GLES30
import me.magnum.melonds.common.opengl.Shader
import me.magnum.melonds.common.opengl.ShaderFactory
import me.magnum.melonds.common.opengl.VideoFilterShaderProvider
import me.magnum.melonds.domain.model.DsExternalScreen
import me.magnum.melonds.domain.model.SCREEN_HEIGHT
import me.magnum.melonds.domain.model.SCREEN_WIDTH
import me.magnum.melonds.domain.model.VideoFiltering
import me.magnum.melonds.domain.model.consoleAspectRatio
import me.magnum.melonds.domain.model.render.PresentFrameWrapper
import me.magnum.melonds.ui.RdsRotation
import me.magnum.melonds.ui.emulator.model.RuntimeRendererConfiguration
import java.nio.ByteBuffer
import java.nio.ByteOrder

class ExternalScreenRender(
    private val screen: DsExternalScreen,
    private var rotateLeft: Boolean,
    private var keepAspectRatio: Boolean,
) : EmulatorRenderer {

    private var shader: Shader? = null
    private var screensVbo = 0
    private var screensVao = 0

    private var videoFiltering: VideoFiltering = VideoFiltering.NONE

    private var surfaceWidth = 0
    private var surfaceHeight = 0
    private var areVerticesDirty = false
    private var areRenderSettingsDirty = false
    private var viewportLock = Any()

    override fun updateRendererConfiguration(newRendererConfiguration: RuntimeRendererConfiguration?) {
        synchronized(viewportLock) {
            videoFiltering = newRendererConfiguration?.videoFiltering ?: VideoFiltering.NONE
            areRenderSettingsDirty = true
        }
    }

    override fun setLeftRotationEnabled(enabled: Boolean) {
        synchronized(viewportLock) {
            rotateLeft = enabled
            areVerticesDirty = true
        }
    }

    override fun onSurfaceCreated() {
        shader = ShaderFactory.createShaderProgram(
            VideoFilterShaderProvider.getShaderSource(videoFiltering)
        )

        val buffers = IntArray(2)
        GLES30.glGenBuffers(1, buffers, 0)
        GLES30.glGenVertexArrays(1, buffers, 1)
        screensVbo = buffers[0]
        screensVao = buffers[1]
        areVerticesDirty = true
    }

    private fun updateScreenVertices() {
        val (actualWidth, actualHeight) = if (rotateLeft) {
            surfaceHeight to surfaceWidth
        } else {
            surfaceWidth to surfaceHeight
        }

        val coords = if (keepAspectRatio) {
            val surfaceAspectRatio = actualWidth.toFloat() / actualHeight
            if (surfaceAspectRatio > consoleAspectRatio) {
                val screenScale = actualHeight.toFloat() / SCREEN_HEIGHT
                val scaledWidth = SCREEN_WIDTH * screenScale
                val relativeWidth = scaledWidth * 2f / actualWidth
                val halfWidth = relativeWidth / 2f
                floatArrayOf(
                    -halfWidth, -1f,
                    -halfWidth, 1f,
                    halfWidth, 1f,
                    -halfWidth, -1f,
                    halfWidth, 1f,
                    halfWidth, -1f,
                )
            } else {
                val screenScale = actualWidth.toFloat() / SCREEN_WIDTH
                val scaledHeight = SCREEN_HEIGHT * screenScale
                val relativeHeight = scaledHeight * 2f / actualHeight
                val halfHeight = relativeHeight / 2f
                floatArrayOf(
                    -1f, -halfHeight,
                    -1f, halfHeight,
                    1f, halfHeight,
                    -1f, -halfHeight,
                    1f, halfHeight,
                    1f, -halfHeight,
                )
            }
        } else {
            floatArrayOf(
                -1f, -1f,
                -1f, 1f,
                1f, 1f,
                -1f, -1f,
                1f, 1f,
                1f, -1f
            )
        }

        if (rotateLeft) {
            RdsRotation.rotateLeft(coords)
        }

        val lineRelativeSize = 1f / (SCREEN_HEIGHT * 2 + 2).toFloat()
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
            coords[0], coords[1],   uvs[0], uvs[1], 1f,
            coords[2], coords[3],   uvs[2], uvs[3], 1f,
            coords[4], coords[5],   uvs[4], uvs[5], 1f,
            coords[6], coords[7],   uvs[6], uvs[7], 1f,
            coords[8], coords[9],   uvs[8], uvs[9], 1f,
            coords[10], coords[11], uvs[10], uvs[11], 1f,
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

    private fun updateShader() {
        // Delete previous shader
        shader?.delete()

        val shaderSource = VideoFilterShaderProvider.getShaderSource(videoFiltering)
        shader = ShaderFactory.createShaderProgram(shaderSource)
    }

    override fun onSurfaceChanged(width: Int, height: Int) {
        surfaceWidth = width
        surfaceHeight = height
        areVerticesDirty = true
    }

    override fun drawFrame(presentFrameWrapper: PresentFrameWrapper) {
        synchronized(viewportLock) {
            if (areVerticesDirty) {
                updateScreenVertices()
                areVerticesDirty = false
            }

            if (areRenderSettingsDirty) {
                updateShader()
                areRenderSettingsDirty = false
            }
        }

        if (!presentFrameWrapper.isValidFrame) {
            return
        }

        val textureId = presentFrameWrapper.textureId

        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT)
        shader?.let {
            it.use()
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, textureId)
            GLES30.glBindVertexArray(screensVao)
            GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, screensVbo)
            GLES30.glVertexAttribPointer(it.attribPos, 2, GLES30.GL_FLOAT, false, 5 * Float.SIZE_BYTES, 0)
            GLES30.glVertexAttribPointer(it.attribUv, 2, GLES30.GL_FLOAT, false, 5 * Float.SIZE_BYTES, 2 * Float.SIZE_BYTES)
            GLES30.glVertexAttribPointer(it.attribAlpha, 1, GLES30.GL_FLOAT, false, 5 * Float.SIZE_BYTES, (2 + 2) * Float.SIZE_BYTES)
            GLES30.glUniform1i(it.uniformTex, 0)
            GLES30.glDrawArrays(GLES30.GL_TRIANGLES, 0, 6)
        }
    }

    fun setKeepAspectRatio(keep: Boolean) {
        synchronized(viewportLock) {
            keepAspectRatio = keep
            areVerticesDirty = true
        }
    }
}