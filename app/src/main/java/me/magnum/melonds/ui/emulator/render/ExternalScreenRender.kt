package me.magnum.melonds.ui.emulator.render

import android.opengl.GLES30
import android.opengl.GLES31
import me.magnum.melonds.common.opengl.Shader
import me.magnum.melonds.common.opengl.ShaderFactory
import me.magnum.melonds.common.opengl.ShaderProgramSource
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

    companion object {
        private const val RESPONSE_WEIGHT = 0.333f
    }

    private var shader: Shader? = null
    private var screensVbo = 0
    private var screensVao = 0
    private val screenUvBounds = FloatArray(4)

    private var videoFiltering: VideoFiltering = VideoFiltering.NONE
    private var customShader: ShaderProgramSource? = null

    private var surfaceWidth = 0
    private var surfaceHeight = 0
    private var areVerticesDirty = false
    private var areRenderSettingsDirty = false
    private var viewportLock = Any()
    private var viewportWidth = 0
    private var viewportHeight = 0

    private var historyTexture = 0
    private var historyWidth = 0
    private var historyHeight = 0
    private var historyReadFramebuffer = 0
    private var historyDrawFramebuffer = 0
    private var historyReady = false

    override fun updateRendererConfiguration(newRendererConfiguration: RuntimeRendererConfiguration?) {
        synchronized(viewportLock) {
            val newFiltering = newRendererConfiguration?.videoFiltering ?: VideoFiltering.NONE
            val newCustomShader = newRendererConfiguration?.customShader
            if (videoFiltering != newFiltering || customShader !== newCustomShader) {
                videoFiltering = newFiltering
                customShader = newCustomShader
                areRenderSettingsDirty = true
            }
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
            VideoFilterShaderProvider.getShaderSource(videoFiltering, customShader)
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
                viewportWidth = scaledWidth.toInt()
                viewportHeight = actualHeight
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
                viewportWidth = actualWidth
                viewportHeight = scaledHeight.toInt()
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
            viewportWidth = actualWidth
            viewportHeight = actualHeight
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
        fillUvBounds(uvs, screenUvBounds)

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

    private fun fillUvBounds(uvs: FloatArray, dest: FloatArray) {
        var minU = 1f
        var minV = 1f
        var maxU = 0f
        var maxV = 0f
        for (i in uvs.indices step 2) {
            val u = uvs[i]
            val v = uvs[i + 1]
            if (u < minU) minU = u
            if (v < minV) minV = v
            if (u > maxU) maxU = u
            if (v > maxV) maxV = v
        }
        dest[0] = minU
        dest[1] = minV
        dest[2] = maxU
        dest[3] = maxV
    }

    private fun updateShader() {
        // Delete previous shader
        shader?.delete()

        val shaderSource = VideoFilterShaderProvider.getShaderSource(videoFiltering, customShader)
        shader = ShaderFactory.createShaderProgram(shaderSource)
        historyReady = false
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
        shader?.let { shader ->
            shader.use()

            val requiresHistory = shader.uniformPrevTex >= 0 || shader.uniformPrevWeight >= 0
            val requiresTexSize = shader.uniformTexSize >= 0
            val textureInfo = if ((requiresHistory || requiresTexSize) && textureId != 0) {
                ensureHistoryResources(textureId)
            } else {
                null
            }

            if (shader.uniformViewportSize >= 0) {
                val targetWidth = if (viewportWidth > 0) viewportWidth else surfaceWidth
                val targetHeight = if (viewportHeight > 0) viewportHeight else surfaceHeight
                val safeWidth = if (targetWidth > 0) targetWidth else 1
                val safeHeight = if (targetHeight > 0) targetHeight else 1
                GLES30.glUniform2f(shader.uniformViewportSize, safeWidth.toFloat(), safeHeight.toFloat())
            }
            if (shader.uniformUvBounds >= 0) {
                GLES30.glUniform4f(
                    shader.uniformUvBounds,
                    screenUvBounds[0],
                    screenUvBounds[1],
                    screenUvBounds[2],
                    screenUvBounds[3],
                )
            }

            textureInfo?.let { info ->
                if (shader.uniformTexSize >= 0) {
                    GLES30.glUniform2f(shader.uniformTexSize, info.width.toFloat(), info.height.toFloat())
                }
            }

            if (requiresHistory) {
                historyReady = historyReady && historyTexture != 0 && textureInfo != null
                if (shader.uniformPrevWeight >= 0) {
                    val weight = if (historyReady) RESPONSE_WEIGHT else 0f
                    GLES30.glUniform1f(shader.uniformPrevWeight, weight)
                }

                if (shader.uniformPrevTex >= 0 && historyTexture != 0) {
                    GLES30.glActiveTexture(GLES30.GL_TEXTURE1)
                    GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, historyTexture)
                    GLES30.glUniform1i(shader.uniformPrevTex, 1)
                }
            }

            GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, textureId)
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, shader.textureFiltering)
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, shader.textureFiltering)

            GLES30.glBindVertexArray(screensVao)
            GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, screensVbo)
            if (shader.attribPos >= 0) {
                GLES30.glVertexAttribPointer(shader.attribPos, 2, GLES30.GL_FLOAT, false, 5 * Float.SIZE_BYTES, 0)
            }
            if (shader.attribUv >= 0) {
                GLES30.glVertexAttribPointer(shader.attribUv, 2, GLES30.GL_FLOAT, false, 5 * Float.SIZE_BYTES, 2 * Float.SIZE_BYTES)
            }
            if (shader.attribAlpha >= 0) {
                GLES30.glVertexAttribPointer(shader.attribAlpha, 1, GLES30.GL_FLOAT, false, 5 * Float.SIZE_BYTES, (2 + 2) * Float.SIZE_BYTES)
            }
            if (shader.uniformTex >= 0) {
                GLES30.glUniform1i(shader.uniformTex, 0)
            }
            GLES30.glDrawArrays(GLES30.GL_TRIANGLES, 0, 6)

            if (requiresHistory) {
                GLES30.glActiveTexture(GLES30.GL_TEXTURE1)
                GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, 0)
                GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
            }

            if (textureInfo != null) {
                copyFrameToHistory(textureId, textureInfo)
            } else if (requiresHistory) {
                historyReady = false
            }
        }
    }

    fun setKeepAspectRatio(keep: Boolean) {
        synchronized(viewportLock) {
            keepAspectRatio = keep
            areVerticesDirty = true
        }
    }

    private fun ensureHistoryResources(sourceTextureId: Int): TextureSize? {
        val size = getTextureDimensions(sourceTextureId) ?: return null

        if (historyTexture == 0 || size.width != historyWidth || size.height != historyHeight) {
            if (historyTexture != 0) {
                val textures = intArrayOf(historyTexture)
                GLES30.glDeleteTextures(1, textures, 0)
                historyTexture = 0
            }

            val textures = IntArray(1)
            GLES30.glGenTextures(1, textures, 0)
            historyTexture = textures[0]

            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, historyTexture)
            GLES30.glTexImage2D(
                GLES30.GL_TEXTURE_2D,
                0,
                GLES30.GL_RGBA,
                size.width,
                size.height,
                0,
                GLES30.GL_RGBA,
                GLES30.GL_UNSIGNED_BYTE,
                null
            )
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_NEAREST)
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_NEAREST)
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE)
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE)
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, 0)

            historyWidth = size.width
            historyHeight = size.height
            historyReady = false
        }

        if (historyReadFramebuffer == 0 || historyDrawFramebuffer == 0) {
            val framebuffers = IntArray(2)
            GLES30.glGenFramebuffers(2, framebuffers, 0)
            historyReadFramebuffer = framebuffers[0]
            historyDrawFramebuffer = framebuffers[1]
        }

        return size
    }

    private fun copyFrameToHistory(sourceTextureId: Int, size: TextureSize) {
        if (historyTexture == 0 || historyReadFramebuffer == 0 || historyDrawFramebuffer == 0) {
            historyReady = false
            return
        }

        GLES30.glBindFramebuffer(GLES30.GL_READ_FRAMEBUFFER, historyReadFramebuffer)
        GLES30.glFramebufferTexture2D(
            GLES30.GL_READ_FRAMEBUFFER,
            GLES30.GL_COLOR_ATTACHMENT0,
            GLES30.GL_TEXTURE_2D,
            sourceTextureId,
            0
        )

        GLES30.glBindFramebuffer(GLES30.GL_DRAW_FRAMEBUFFER, historyDrawFramebuffer)
        GLES30.glFramebufferTexture2D(
            GLES30.GL_DRAW_FRAMEBUFFER,
            GLES30.GL_COLOR_ATTACHMENT0,
            GLES30.GL_TEXTURE_2D,
            historyTexture,
            0
        )

        GLES30.glBlitFramebuffer(
            0,
            0,
            size.width,
            size.height,
            0,
            0,
            size.width,
            size.height,
            GLES30.GL_COLOR_BUFFER_BIT,
            GLES30.GL_NEAREST
        )

        GLES30.glBindFramebuffer(GLES30.GL_READ_FRAMEBUFFER, 0)
        GLES30.glBindFramebuffer(GLES30.GL_DRAW_FRAMEBUFFER, 0)

        historyReady = true
    }

    private fun getTextureDimensions(textureId: Int): TextureSize? {
        if (textureId == 0) {
            return null
        }

        val previousBinding = IntArray(1)
        GLES30.glGetIntegerv(GLES30.GL_TEXTURE_BINDING_2D, previousBinding, 0)

        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, textureId)
        val width = IntArray(1)
        val height = IntArray(1)
        GLES31.glGetTexLevelParameteriv(GLES30.GL_TEXTURE_2D, 0, GLES31.GL_TEXTURE_WIDTH, width, 0)
        GLES31.glGetTexLevelParameteriv(GLES30.GL_TEXTURE_2D, 0, GLES31.GL_TEXTURE_HEIGHT, height, 0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, previousBinding[0])

        if (width[0] <= 0 || height[0] <= 0) {
            return null
        }

        return TextureSize(width[0], height[0])
    }

    private data class TextureSize(val width: Int, val height: Int)
}