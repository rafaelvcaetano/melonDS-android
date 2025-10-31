package me.magnum.melonds.ui.emulator

import android.content.Context
import android.graphics.BitmapFactory
import android.opengl.GLES30
import android.opengl.GLES31
import android.opengl.GLUtils
import me.magnum.melonds.common.opengl.Shader
import me.magnum.melonds.common.opengl.ShaderFactory
import me.magnum.melonds.common.opengl.ShaderProgramSource
import me.magnum.melonds.common.opengl.VideoFilterShaderProvider
import me.magnum.melonds.domain.model.Rect
import me.magnum.melonds.domain.model.RuntimeBackground
import me.magnum.melonds.domain.model.SCREEN_HEIGHT
import me.magnum.melonds.domain.model.VideoFiltering
import me.magnum.melonds.domain.model.layout.BackgroundMode
import me.magnum.melonds.domain.model.render.PresentFrameWrapper
import me.magnum.melonds.ui.emulator.model.RuntimeRendererConfiguration
import me.magnum.melonds.ui.emulator.render.EmulatorRenderer
import me.magnum.melonds.utils.BitmapUtils
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

class DSRenderer(private val context: Context) : EmulatorRenderer {

    companion object {
        private const val RESPONSE_WEIGHT = 0.333f
    }

    private data class TextureSize(val width: Int, val height: Int)

    private data class ScreenVertexData(
        val vertices: FloatArray,
        val vertexCount: Int,
        val viewportWidth: Float,
        val viewportHeight: Float,
        val uvMinX: Float,
        val uvMinY: Float,
        val uvMaxX: Float,
        val uvMaxY: Float,
    )

    private data class ScreenDrawCall(
        val firstVertex: Int,
        val vertexCount: Int,
        val viewportWidth: Float,
        val viewportHeight: Float,
        val uvMinX: Float,
        val uvMinY: Float,
        val uvMaxX: Float,
        val uvMaxY: Float,
    )

    private var rendererConfiguration: RuntimeRendererConfiguration? = null
    private var mustUpdateConfiguration = false
    private var isBackgroundPositionDirty = false
    private var isBackgroundLoaded = false

    private var backgroundTexture = 0

    private var screenShader: Shader? = null
    private lateinit var backgroundShader: Shader

    private var screensVbo = 0
    private var screensVao = 0
    private val screenDrawCalls = mutableListOf<ScreenDrawCall>()

    private var historyTexture = 0
    private var historyWidth = 0
    private var historyHeight = 0
    private var historyReadFramebuffer = 0
    private var historyDrawFramebuffer = 0
    private var historyReady = false

    private var backgroundVbo = 0
    private var backgroundVao = 0

    // Lock used when manipulating background properties
    private val backgroundLock = Any()
    private var background: RuntimeBackground? = null
    private var mustLoadBackground = false
    private var topScreenRect: Rect? = null
    private var bottomScreenRect: Rect? = null
    private var topAlpha: Float = 1f
    private var bottomAlpha: Float = 1f
    private var topOnTop: Boolean = false
    private var bottomOnTop: Boolean = false

    private var width = 0f
    private var height = 0f

    private var backgroundWidth = 0
    private var backgroundHeight = 0

    override fun updateRendererConfiguration(newRendererConfiguration: RuntimeRendererConfiguration?) {
        rendererConfiguration = newRendererConfiguration
        mustUpdateConfiguration = true
    }

    override fun setLeftRotationEnabled(enabled: Boolean) {
    }

    fun updateScreenAreas(
        topScreenRect: Rect?,
        bottomScreenRect: Rect?,
        topAlpha: Float,
        bottomAlpha: Float,
        topOnTop: Boolean,
        bottomOnTop: Boolean,
    ) {
        this.topScreenRect = topScreenRect
        this.bottomScreenRect = bottomScreenRect
        this.topAlpha = topAlpha
        this.bottomAlpha = bottomAlpha
        this.topOnTop = topOnTop
        this.bottomOnTop = bottomOnTop
        mustUpdateConfiguration = true
    }

    fun setBackground(background: RuntimeBackground) {
        synchronized(backgroundLock) {
            this.background = background
            mustLoadBackground = true
            isBackgroundPositionDirty = true
            isBackgroundLoaded = false
        }
    }

    private fun screenXToViewportX(x: Int): Float {
        return (x / width) * 2f - 1f
    }

    private fun screenYToViewportY(y: Int): Float {
        return 1f - y / height * 2f
    }

    override fun onSurfaceCreated() {
        GLES30.glClearColor(0f, 0f, 0f, 1f)
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT or GLES30.GL_DEPTH_BUFFER_BIT)
        GLES30.glDisable(GLES30.GL_CULL_FACE)

        // Setup textures
        val textures = IntArray(1)
        GLES30.glGenTextures(1, textures, 0)
        backgroundTexture = textures[0]

        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, backgroundTexture)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)

        // Setup vertex buffers
        val vbos = IntArray(2)
        val vaos = IntArray(2)
        GLES30.glGenBuffers(2, vbos, 0)
        GLES30.glGenVertexArrays(2, vaos, 0)
        screensVbo = vbos[0]
        screensVao = vaos[1]

        backgroundVbo = vbos[1]
        backgroundVao = vaos[1]

        // Create background shader
        backgroundShader = ShaderFactory.createShaderProgram(ShaderProgramSource.BackgroundShader)

        applyRendererConfiguration()
    }

    private fun applyRendererConfiguration() {
        updateScreenCoordinates()
        updateShader()
    }

    private fun updateScreenCoordinates() {
        // Indices:
        // 1                         2
        //   +-----------------------+ 4
        //   |                       |
        //   |                       |
        //   |                       |
        //   |                       |
        // 0 +-----------------------+
        //   3                         5
        // Texture is vertically flipped

        // The texture will have 2 empty lines between the screens. Take that into account when computing UVs
        val lineRelativeSize = 1 / (SCREEN_HEIGHT + 1).toFloat()

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

        val screenDataList = mutableListOf<ScreenVertexData>()
        if (bottomOnTop) {
            topScreenRect?.let { screenDataList.add(buildScreenVertexData(it, topUvs, topAlpha)) }
            bottomScreenRect?.let { screenDataList.add(buildScreenVertexData(it, bottomUvs, bottomAlpha)) }
        } else {
            bottomScreenRect?.let { screenDataList.add(buildScreenVertexData(it, bottomUvs, bottomAlpha)) }
            topScreenRect?.let { screenDataList.add(buildScreenVertexData(it, topUvs, topAlpha)) }
        }

        val vertexElementCount = screenDataList.sumOf { it.vertices.size }
        val vertexBufferSize = vertexElementCount * Float.SIZE_BYTES
        GLES30.glBindVertexArray(screensVao)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, screensVbo)
        if (vertexBufferSize > 0) {
            val vertexBuffer = ByteBuffer.allocateDirect(vertexBufferSize)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer()
                .apply {
                    screenDataList.forEach { put(it.vertices) }
                    position(0)
                }
            GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, vertexBufferSize, vertexBuffer, GLES30.GL_STATIC_DRAW)
        } else {
            GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, 0, null, GLES30.GL_STATIC_DRAW)
        }

        screenDrawCalls.clear()
        var firstVertex = 0
        screenDataList.forEach { data ->
            screenDrawCalls.add(
                ScreenDrawCall(
                    firstVertex = firstVertex,
                    vertexCount = data.vertexCount,
                    viewportWidth = data.viewportWidth,
                    viewportHeight = data.viewportHeight,
                    uvMinX = data.uvMinX,
                    uvMinY = data.uvMinY,
                    uvMaxX = data.uvMaxX,
                    uvMaxY = data.uvMaxY,
                )
            )
            firstVertex += data.vertexCount
        }
    }

    private fun buildScreenVertexData(rect: Rect, uvs: FloatArray, alpha: Float): ScreenVertexData {
        val left = screenXToViewportX(rect.x)
        val right = screenXToViewportX(rect.x + rect.width)
        val top = screenYToViewportY(rect.y)
        val bottom = screenYToViewportY(rect.y + rect.height)

        val vertices = floatArrayOf(
            // Position    UVs               Alpha
            left, bottom,  uvs[0], uvs[1],   alpha,
            left, top,     uvs[2], uvs[3],   alpha,
            right, top,    uvs[4], uvs[5],   alpha,
            left, bottom,  uvs[6], uvs[7],   alpha,
            right, top,    uvs[8], uvs[9],   alpha,
            right, bottom, uvs[10], uvs[11], alpha,
        )

        val viewportWidth = rect.width.toFloat().coerceAtLeast(1f)
        val viewportHeight = rect.height.toFloat().coerceAtLeast(1f)
        var uvMinX = 1f
        var uvMinY = 1f
        var uvMaxX = 0f
        var uvMaxY = 0f
        for (i in uvs.indices step 2) {
            val u = uvs[i]
            val v = uvs[i + 1]
            uvMinX = min(uvMinX, u)
            uvMinY = min(uvMinY, v)
            uvMaxX = max(uvMaxX, u)
            uvMaxY = max(uvMaxY, v)
        }

        return ScreenVertexData(
            vertices = vertices,
            vertexCount = vertices.size / 5,
            viewportWidth = viewportWidth,
            viewportHeight = viewportHeight,
            uvMinX = uvMinX,
            uvMinY = uvMinY,
            uvMaxX = uvMaxX,
            uvMaxY = uvMaxY,
        )
    }

    private fun updateShader() {
        // Delete previous shader
        screenShader?.delete()

        val filtering = rendererConfiguration?.videoFiltering ?: VideoFiltering.NONE
        val shaderSource = VideoFilterShaderProvider.getShaderSource(filtering)
        screenShader = ShaderFactory.createShaderProgram(shaderSource)
        historyReady = false
    }

    override fun onSurfaceChanged(width: Int, height: Int) {
        this.width = width.toFloat()
        this.height = height.toFloat()
        mustUpdateConfiguration = true

        synchronized(backgroundLock) {
            isBackgroundPositionDirty = true
        }
    }

    override fun drawFrame(presentFrameWrapper: PresentFrameWrapper) {
        if (mustUpdateConfiguration) {
            applyRendererConfiguration()
            mustUpdateConfiguration = false
        }

        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT or GLES30.GL_DEPTH_BUFFER_BIT)

        if (!presentFrameWrapper.isValidFrame) {
            return
        }

        synchronized(backgroundLock) {
            renderBackground()
        }

        screenShader?.let { shader ->
            shader.use()

            GLES30.glDisable(GLES30.GL_DEPTH_TEST)
            GLES30.glEnable(GLES30.GL_BLEND)
            GLES30.glBlendFunc(GLES30.GL_SRC_ALPHA, GLES30.GL_ONE_MINUS_SRC_ALPHA)

            val requiresHistory = shader.uniformPrevTex >= 0 || shader.uniformPrevWeight >= 0
            val requiresTexSize = shader.uniformTexSize >= 0
            val textureInfo = if ((requiresHistory || requiresTexSize) && presentFrameWrapper.textureId != 0) {
                ensureHistoryResources(presentFrameWrapper.textureId)
            } else {
                null
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
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, presentFrameWrapper.textureId)
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, shader.textureFiltering)
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, shader.textureFiltering)

            GLES30.glBindVertexArray(screensVao)
            GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, screensVbo)

            GLES30.glVertexAttribPointer(shader.attribPos, 2, GLES30.GL_FLOAT, false, 5 * Float.SIZE_BYTES, 0)
            GLES30.glVertexAttribPointer(shader.attribUv, 2, GLES30.GL_FLOAT, false, 5 * Float.SIZE_BYTES, 2 * Float.SIZE_BYTES)
            GLES30.glVertexAttribPointer(shader.attribAlpha, 1, GLES30.GL_FLOAT, false, 5 * Float.SIZE_BYTES, 4 * Float.SIZE_BYTES)
            GLES30.glUniform1i(shader.uniformTex, 0)

            screenDrawCalls.forEach { drawCall ->
                if (shader.uniformViewportSize >= 0) {
                    GLES30.glUniform2f(
                        shader.uniformViewportSize,
                        drawCall.viewportWidth,
                        drawCall.viewportHeight,
                    )
                }
                if (shader.uniformUvBounds >= 0) {
                    GLES30.glUniform4f(
                        shader.uniformUvBounds,
                        drawCall.uvMinX,
                        drawCall.uvMinY,
                        drawCall.uvMaxX,
                        drawCall.uvMaxY,
                    )
                }
                GLES30.glDrawArrays(GLES30.GL_TRIANGLES, drawCall.firstVertex, drawCall.vertexCount)
            }

            if (requiresHistory) {
                GLES30.glActiveTexture(GLES30.GL_TEXTURE1)
                GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, 0)
                GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
            }

            GLES30.glBindVertexArray(0)
            GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, 0)

            if (textureInfo != null) {
                copyFrameToHistory(presentFrameWrapper.textureId, textureInfo)
            } else if (requiresHistory) {
                historyReady = false
            }
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

    private fun renderBackground() {
        if (mustLoadBackground) {
            loadBackground()
            mustLoadBackground = false
        }

        if (!isBackgroundLoaded) {
            return
        }

        if (isBackgroundPositionDirty) {
            updateBackgroundPosition()
        }

        backgroundShader.use()
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, backgroundTexture)

        GLES30.glBindVertexArray(backgroundVao)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, backgroundVbo)
        GLES30.glVertexAttribPointer(backgroundShader.attribPos, 2, GLES30.GL_FLOAT, false, 4 * Float.SIZE_BYTES, 0)
        GLES30.glVertexAttribPointer(backgroundShader.attribUv, 2, GLES30.GL_FLOAT, false, 4 * Float.SIZE_BYTES, 2 * Float.SIZE_BYTES)
        GLES30.glUniform1i(backgroundShader.uniformTex, 0)

        GLES30.glDrawArrays(GLES30.GL_TRIANGLES, 0, 6)
    }

    private fun loadBackground() {
        val background = background ?: return

        background.background?.uri?.let {
            val backgroundSampleSize = BitmapUtils.calculateMinimumSampleSize(context, it, width.roundToInt(), height.roundToInt())

            val bitmapResult = runCatching {
                context.contentResolver.openInputStream(it)?.let { stream ->
                    val options = BitmapFactory.Options().apply {
                        inSampleSize = backgroundSampleSize
                    }

                    BitmapFactory.decodeStream(stream, null, options)
                }
            }

            val bitmap = bitmapResult.getOrNull() ?: return

            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, backgroundTexture)
            GLUtils.texImage2D(GLES30.GL_TEXTURE_2D, 0, bitmap, 0)
            bitmap.recycle()

            backgroundWidth = bitmap.width
            backgroundHeight = bitmap.height

            isBackgroundLoaded = true
            isBackgroundPositionDirty = true
        }
    }

    private fun updateBackgroundPosition() {
        val background = background ?: return
        val coords = getBackgroundCoords(background.mode, backgroundWidth, backgroundHeight)

        val vertexData = floatArrayOf(
            // Position             UVs
            coords[0], coords[1],   0f, 1f,
            coords[2], coords[3],   0f, 0f,
            coords[4], coords[5],   1f, 0f,
            coords[6], coords[7],   0f, 1f,
            coords[8], coords[9],   1f, 0f,
            coords[10], coords[11], 1f, 1f,
        )

        val vertexBufferSize = vertexData.size * Float.SIZE_BYTES
        val vertexBuffer = ByteBuffer.allocateDirect(vertexBufferSize)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .put(vertexData)
            .position(0)

        GLES30.glBindVertexArray(backgroundVao)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, backgroundVbo)
        GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, vertexBufferSize, vertexBuffer, GLES30.GL_STATIC_DRAW)

        isBackgroundPositionDirty = false
    }

    private fun getBackgroundCoords(backgroundMode: BackgroundMode, backgroundWidth: Int, backgroundHeight: Int): Array<Float> {
        val backgroundAspectRatio = backgroundWidth / backgroundHeight.toFloat()
        val screenAspectRatio = width / height

        return when (backgroundMode) {
            BackgroundMode.STRETCH -> {
                arrayOf(
                    -1f, -1f,
                    -1f, 1f,
                    1f, 1f,
                    -1f, -1f,
                    1f, 1f,
                    1f, -1f
                )
            }
            BackgroundMode.FIT_CENTER -> {
                if (screenAspectRatio > backgroundAspectRatio) {
                    val scaleFactor = width / backgroundWidth
                    val relativeBackgroundWidth = height / (backgroundHeight * scaleFactor) * 2f
                    arrayOf(
                        -(relativeBackgroundWidth / 2f), -1f,
                        -(relativeBackgroundWidth / 2f), 1f,
                        relativeBackgroundWidth / 2f, 1f,
                        -(relativeBackgroundWidth / 2f), -1f,
                        relativeBackgroundWidth / 2f, 1f,
                        relativeBackgroundWidth / 2f, -1f
                    )
                } else {
                    val scaleFactor = height / backgroundHeight
                    val relativeBackgroundHeight = width / (backgroundWidth * scaleFactor) * 2f
                    arrayOf(
                        -1f, -(relativeBackgroundHeight / 2f),
                        -1f, relativeBackgroundHeight / 2f,
                        1f, relativeBackgroundHeight / 2f,
                        -1f, -(relativeBackgroundHeight / 2f),
                        1f, relativeBackgroundHeight / 2f,
                        1f, -(relativeBackgroundHeight / 2f)
                    )
                }
            }
            BackgroundMode.FIT_LEFT -> {
                if (screenAspectRatio > backgroundAspectRatio) {
                    val scaleFactor = width / backgroundWidth
                    val relativeBackgroundWidth = height / (backgroundHeight * scaleFactor) * 2f
                    arrayOf(
                        -1f, -1f,
                        -1f, 1f,
                        -1 + relativeBackgroundWidth, 1f,
                        -1f, -1f,
                        -1 + relativeBackgroundWidth, 1f,
                        -1 + relativeBackgroundWidth, -1f
                    )
                } else {
                    val scaleFactor = height / backgroundHeight
                    val relativeBackgroundHeight = width / (backgroundWidth * scaleFactor) * 2f
                    arrayOf(
                        -1f, -(relativeBackgroundHeight / 2f),
                        -1f, relativeBackgroundHeight / 2f,
                        1f, relativeBackgroundHeight / 2f,
                        -1f, -(relativeBackgroundHeight / 2f),
                        1f, relativeBackgroundHeight / 2f,
                        1f, -(relativeBackgroundHeight / 2f)
                    )
                }
            }
            BackgroundMode.FIT_RIGHT -> {
                if (screenAspectRatio > backgroundAspectRatio) {
                    val scaleFactor = width / backgroundWidth
                    val relativeBackgroundWidth = height / (backgroundHeight * scaleFactor) * 2f
                    arrayOf(
                        1f - relativeBackgroundWidth, -1f,
                        1f - relativeBackgroundWidth, 1f,
                        1f, 1f,
                        1f - relativeBackgroundWidth, -1f,
                        1f, 1f,
                        1f, -1f
                    )
                } else {
                    val scaleFactor = height / backgroundHeight
                    val relativeBackgroundHeight = width / (backgroundWidth * scaleFactor) * 2f
                    arrayOf(
                        -1f, -(relativeBackgroundHeight / 2f),
                        -1f, relativeBackgroundHeight / 2f,
                        1f, relativeBackgroundHeight / 2f,
                        -1f, -(relativeBackgroundHeight / 2f),
                        1f, relativeBackgroundHeight / 2f,
                        1f, -(relativeBackgroundHeight / 2f)
                    )
                }
            }
            BackgroundMode.FIT_TOP -> {
                if (screenAspectRatio > backgroundAspectRatio) {
                    val scaleFactor = width / backgroundWidth
                    val relativeBackgroundWidth = height / (backgroundHeight * scaleFactor) * 2f
                    arrayOf(
                        -(relativeBackgroundWidth / 2f), -1f,
                        -(relativeBackgroundWidth / 2f), 1f,
                        relativeBackgroundWidth / 2f, 1f,
                        -(relativeBackgroundWidth / 2f), -1f,
                        relativeBackgroundWidth / 2f, 1f,
                        relativeBackgroundWidth / 2f, -1f
                    )
                } else {
                    val scaleFactor = height / backgroundHeight
                    val relativeBackgroundHeight = width / (backgroundWidth * scaleFactor) * 2f
                    arrayOf(
                        -1f, 1f - relativeBackgroundHeight,
                        -1f, 1f,
                        1f, 1f,
                        -1f, 1f - relativeBackgroundHeight,
                        1f, 1f,
                        1f, 1f - relativeBackgroundHeight
                    )
                }
            }
            BackgroundMode.FIT_BOTTOM -> {
                if (screenAspectRatio > backgroundAspectRatio) {
                    val scaleFactor = width / backgroundWidth
                    val relativeBackgroundWidth = height / (backgroundHeight * scaleFactor) * 2f
                    arrayOf(
                        -(relativeBackgroundWidth / 2f), -1f,
                        -(relativeBackgroundWidth / 2f), 1f,
                        relativeBackgroundWidth / 2f, 1f,
                        -(relativeBackgroundWidth / 2f), -1f,
                        relativeBackgroundWidth / 2f, 1f,
                        relativeBackgroundWidth / 2f, -1f
                    )
                } else {
                    val scaleFactor = height / backgroundHeight
                    val relativeBackgroundHeight = width / (backgroundWidth * scaleFactor) * 2f
                    arrayOf(
                        -1f, -1f,
                        -1f, -1f + relativeBackgroundHeight,
                        1f, -1f + relativeBackgroundHeight,
                        -1f, -1f,
                        1f, -1f + relativeBackgroundHeight,
                        1f, -1f
                    )
                }
            }
        }
    }
}