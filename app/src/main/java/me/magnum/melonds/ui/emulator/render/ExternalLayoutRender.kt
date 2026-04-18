package me.magnum.melonds.ui.emulator.render

import android.content.Context
import android.graphics.BitmapFactory
import android.opengl.GLES30
import android.opengl.GLSurfaceView
import android.opengl.GLUtils
import me.magnum.melonds.common.opengl.Shader
import me.magnum.melonds.common.opengl.ShaderFactory
import me.magnum.melonds.common.opengl.ShaderProgramSource
import me.magnum.melonds.common.opengl.VideoFilterShaderProvider
import me.magnum.melonds.domain.model.Rect
import me.magnum.melonds.domain.model.RuntimeBackground
import me.magnum.melonds.domain.model.VideoFiltering
import me.magnum.melonds.domain.model.layout.BackgroundMode
import me.magnum.melonds.domain.model.render.PresentFrameWrapper
import me.magnum.melonds.ui.RdsRotation
import me.magnum.melonds.ui.emulator.model.RuntimeRendererConfiguration
import me.magnum.melonds.utils.BitmapUtils
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

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
    private val context: Context,
    private var topScreen: Rect?,
    private var bottomScreen: Rect?,
    private var layoutWidth: Int,
    private var layoutHeight: Int,
    private var topAlpha: Float = 1f,
    private var bottomAlpha: Float = 1f,
    private var topOnTop: Boolean = false,
    private var bottomOnTop: Boolean = false,
    private var background: RuntimeBackground = RuntimeBackground.None,
    private val rotateLeft: Boolean,
) : EmulatorRenderer {

    companion object {
        private const val TOTAL_SCREEN_HEIGHT = 384
    }

    private lateinit var shader: Shader

    private var posTop: FloatBuffer? = null
    private var posBottom: FloatBuffer? = null

    private lateinit var uvTop: FloatBuffer
    private lateinit var uvBottom: FloatBuffer

    private var videoFiltering: VideoFiltering = VideoFiltering.NONE

    private var viewWidth = 0
    private var viewHeight = 0

    private var backgroundTexture = 0
    private lateinit var backgroundShader: Shader
    private lateinit var backgroundPosBuffer: FloatBuffer
    private lateinit var backgroundUvBuffer: FloatBuffer
    private val backgroundLock = Any()
    private var mustLoadBackground = false
    private var isBackgroundLoaded = false
    private var isBackgroundPositionDirty = false
    private var backgroundWidth = 0
    private var backgroundHeight = 0

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
        synchronized(backgroundLock) {
            isBackgroundPositionDirty = true
        }
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

        if (rotateLeft) {
            RdsRotation.rotateLeft(coords)
        }

        return ByteBuffer.allocateDirect(coords.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .put(coords)
    }

    private fun updateBuffers() {
        posTop = topScreen?.let { rectToBuffer(it) }
        posBottom = bottomScreen?.let { rectToBuffer(it) }
    }

    fun setBackground(background: RuntimeBackground) {
        synchronized(backgroundLock) {
            this.background = background
            mustLoadBackground = true
            isBackgroundLoaded = false
            isBackgroundPositionDirty = true
        }
    }

    override fun updateRendererConfiguration(newRendererConfiguration: RuntimeRendererConfiguration?) {
    }

    override fun setLeftRotationEnabled(enabled: Boolean) {
    }

    override fun onSurfaceCreated() {
        shader = ShaderFactory.createShaderProgram(
            VideoFilterShaderProvider.getShaderSource(videoFiltering)
        )

        val textures = IntArray(1)
        GLES30.glGenTextures(1, textures, 0)
        backgroundTexture = textures[0]
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, backgroundTexture)
        GLES30.glTexParameterf(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE.toFloat())
        GLES30.glTexParameterf(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE.toFloat())
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)

        backgroundShader = ShaderFactory.createShaderProgram(ShaderProgramSource.BackgroundShader)
        val lineRelativeSize = 1f / (TOTAL_SCREEN_HEIGHT + 2).toFloat()
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

    override fun onSurfaceChanged(width: Int, height: Int) {
        viewWidth = width
        viewHeight = height
        updateBuffers()
        synchronized(backgroundLock) {
            isBackgroundPositionDirty = true
        }
    }

    override fun drawFrame(presentFrameWrapper: PresentFrameWrapper) {
        if (!presentFrameWrapper.isValidFrame) {
            return
        }

        val textureId = presentFrameWrapper.textureId

        GLES30.glViewport(0, 0, viewWidth, viewHeight)
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT)

        synchronized(backgroundLock) {
            renderBackground()
        }

        shader.use()
        GLES30.glDisableVertexAttribArray(shader.attribAlpha)

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

    /**
     * Updates the video filtering setting used by the renderer.
     *
     * If the shader has already been initialized, it will be deleted and a new
     * shader will be created with the updated filtering.
     *
     * @param videoFiltering The new [VideoFiltering] setting to apply.
     */
    fun updateVideoFiltering(videoFiltering: VideoFiltering) {
        this.videoFiltering = videoFiltering
        if (this::shader.isInitialized) {
            shader.delete()
            shader = ShaderFactory.createShaderProgram(
                VideoFilterShaderProvider.getShaderSource(videoFiltering)
            )
        }
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

        backgroundPosBuffer.position(0)
        backgroundUvBuffer.position(0)

        val indices = backgroundPosBuffer.capacity() / 2
        backgroundShader.use()
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, backgroundTexture)
        GLES30.glVertexAttribPointer(backgroundShader.attribPos, 2, GLES30.GL_FLOAT, false, 0, backgroundPosBuffer)
        GLES30.glVertexAttribPointer(backgroundShader.attribUv, 2, GLES30.GL_FLOAT, false, 0, backgroundUvBuffer)
        GLES30.glUniform1i(backgroundShader.uniformTex, 0)
        GLES30.glDrawArrays(GLES30.GL_TRIANGLES, 0, indices)
    }

    private fun loadBackground() {
        background.background?.uri?.let {
            val sample = BitmapUtils.calculateMinimumSampleSize(context, it, viewWidth, viewHeight)

            val bitmapResult = runCatching {
                context.contentResolver.openInputStream(it)?.let { stream ->
                    val options = BitmapFactory.Options().apply {
                        inSampleSize = sample
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

            val uvs = arrayOf(
                0f, 1f,
                0f, 0f,
                1f, 0f,
                0f, 1f,
                1f, 0f,
                1f, 1f,
            )

            backgroundUvBuffer = ByteBuffer.allocateDirect(4 * uvs.size)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer()
                .put(uvs.toFloatArray())

            isBackgroundLoaded = true
            isBackgroundPositionDirty = true
        }
    }

    private fun updateBackgroundPosition() {
        val coords = getBackgroundCoords(background.mode, backgroundWidth, backgroundHeight)
        val array = coords.toFloatArray()
        if (rotateLeft) {
            RdsRotation.rotateLeft(array)
        }

        backgroundPosBuffer = ByteBuffer.allocateDirect(4 * array.size)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .put(array)

        isBackgroundPositionDirty = false
    }

    private fun getBackgroundCoords(mode: BackgroundMode, bgWidth: Int, bgHeight: Int): Array<Float> {
        val backgroundAspectRatio = bgWidth / bgHeight.toFloat()
        val screenAspectRatio = viewWidth / viewHeight.toFloat()

        return when (mode) {
            BackgroundMode.STRETCH -> arrayOf(
                -1f, -1f,
                -1f, 1f,
                1f, 1f,
                -1f, -1f,
                1f, 1f,
                1f, -1f,
            )
            BackgroundMode.FIT_CENTER -> if (screenAspectRatio > backgroundAspectRatio) {
                val scaleFactor = viewWidth / bgWidth.toFloat()
                val relativeBackgroundWidth = viewHeight / (bgHeight * scaleFactor) * 2f
                arrayOf(
                    -(relativeBackgroundWidth / 2f), -1f,
                    -(relativeBackgroundWidth / 2f), 1f,
                    relativeBackgroundWidth / 2f, 1f,
                    -(relativeBackgroundWidth / 2f), -1f,
                    relativeBackgroundWidth / 2f, 1f,
                    relativeBackgroundWidth / 2f, -1f,
                )
            } else {
                val scaleFactor = viewHeight / bgHeight.toFloat()
                val relativeBackgroundHeight = viewWidth / (bgWidth * scaleFactor) * 2f
                arrayOf(
                    -1f, -(relativeBackgroundHeight / 2f),
                    -1f, relativeBackgroundHeight / 2f,
                    1f, relativeBackgroundHeight / 2f,
                    -1f, -(relativeBackgroundHeight / 2f),
                    1f, relativeBackgroundHeight / 2f,
                    1f, -(relativeBackgroundHeight / 2f),
                )
            }
            BackgroundMode.FIT_LEFT -> if (screenAspectRatio > backgroundAspectRatio) {
                val scaleFactor = viewWidth / bgWidth.toFloat()
                val relativeBackgroundWidth = viewHeight / (bgHeight * scaleFactor) * 2f
                arrayOf(
                    -1f, -1f,
                    -1f, 1f,
                    -1 + relativeBackgroundWidth, 1f,
                    -1f, -1f,
                    -1 + relativeBackgroundWidth, 1f,
                    -1 + relativeBackgroundWidth, -1f,
                )
            } else {
                val scaleFactor = viewHeight / bgHeight.toFloat()
                val relativeBackgroundHeight = viewWidth / (bgWidth * scaleFactor) * 2f
                arrayOf(
                    -1f, -(relativeBackgroundHeight / 2f),
                    -1f, relativeBackgroundHeight / 2f,
                    1f, relativeBackgroundHeight / 2f,
                    -1f, -(relativeBackgroundHeight / 2f),
                    1f, relativeBackgroundHeight / 2f,
                    1f, -(relativeBackgroundHeight / 2f),
                )
            }
            BackgroundMode.FIT_RIGHT -> if (screenAspectRatio > backgroundAspectRatio) {
                val scaleFactor = viewWidth / bgWidth.toFloat()
                val relativeBackgroundWidth = viewHeight / (bgHeight * scaleFactor) * 2f
                arrayOf(
                    1f - relativeBackgroundWidth, -1f,
                    1f - relativeBackgroundWidth, 1f,
                    1f, 1f,
                    1f - relativeBackgroundWidth, -1f,
                    1f, 1f,
                    1f, -1f,
                )
            } else {
                val scaleFactor = viewHeight / bgHeight.toFloat()
                val relativeBackgroundHeight = viewWidth / (bgWidth * scaleFactor) * 2f
                arrayOf(
                    -1f, -(relativeBackgroundHeight / 2f),
                    -1f, relativeBackgroundHeight / 2f,
                    1f, relativeBackgroundHeight / 2f,
                    -1f, -(relativeBackgroundHeight / 2f),
                    1f, relativeBackgroundHeight / 2f,
                    1f, -(relativeBackgroundHeight / 2f),
                )
            }
            BackgroundMode.FIT_TOP -> if (screenAspectRatio > backgroundAspectRatio) {
                val scaleFactor = viewWidth / bgWidth.toFloat()
                val relativeBackgroundWidth = viewHeight / (bgHeight * scaleFactor) * 2f
                arrayOf(
                    -(relativeBackgroundWidth / 2f), -1f,
                    -(relativeBackgroundWidth / 2f), 1f,
                    relativeBackgroundWidth / 2f, 1f,
                    -(relativeBackgroundWidth / 2f), -1f,
                    relativeBackgroundWidth / 2f, 1f,
                    relativeBackgroundWidth / 2f, -1f,
                )
            } else {
                val scaleFactor = viewHeight / bgHeight.toFloat()
                val relativeBackgroundHeight = viewWidth / (bgWidth * scaleFactor) * 2f
                arrayOf(
                    -1f, 1f - relativeBackgroundHeight,
                    -1f, 1f,
                    1f, 1f,
                    -1f, 1f - relativeBackgroundHeight,
                    1f, 1f,
                    1f, 1f - relativeBackgroundHeight,
                )
            }
            BackgroundMode.FIT_BOTTOM -> if (screenAspectRatio > backgroundAspectRatio) {
                val scaleFactor = viewWidth / bgWidth.toFloat()
                val relativeBackgroundWidth = viewHeight / (bgHeight * scaleFactor) * 2f
                arrayOf(
                    -(relativeBackgroundWidth / 2f), -1f,
                    -(relativeBackgroundWidth / 2f), 1f,
                    relativeBackgroundWidth / 2f, 1f,
                    -(relativeBackgroundWidth / 2f), -1f,
                    relativeBackgroundWidth / 2f, 1f,
                    relativeBackgroundWidth / 2f, -1f,
                )
            } else {
                val scaleFactor = viewHeight / bgHeight.toFloat()
                val relativeBackgroundHeight = viewWidth / (bgWidth * scaleFactor) * 2f
                arrayOf(
                    -1f, -1f,
                    -1f, -1f + relativeBackgroundHeight,
                    1f, -1f + relativeBackgroundHeight,
                    -1f, -1f,
                    1f, -1f + relativeBackgroundHeight,
                    1f, -1f,
                )
            }
        }
    }

}