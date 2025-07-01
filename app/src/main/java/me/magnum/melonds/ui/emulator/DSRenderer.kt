package me.magnum.melonds.ui.emulator

import android.content.Context
import android.graphics.BitmapFactory
import android.opengl.EGL14
import android.opengl.GLES30
import javax.microedition.khronos.egl.EGL10
import android.opengl.GLSurfaceView
import android.opengl.GLUtils
import me.magnum.melonds.common.opengl.Shader
import me.magnum.melonds.common.opengl.ShaderFactory
import me.magnum.melonds.common.opengl.ShaderProgramSource
import me.magnum.melonds.domain.model.Rect
import me.magnum.melonds.ui.emulator.FrameRenderEventConsumer
import me.magnum.melonds.domain.model.RuntimeBackground
import me.magnum.melonds.domain.model.VideoFiltering
import me.magnum.melonds.domain.model.layout.BackgroundMode
import me.magnum.melonds.domain.model.render.FrameRenderEvent
import me.magnum.melonds.ui.emulator.model.RuntimeRendererConfiguration
import me.magnum.melonds.utils.BitmapUtils
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.util.LinkedList
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.roundToInt

class DSRenderer(
    private val context: Context,
    private val onGlContextReady: (glContext: Long) -> Unit,
) : GLSurfaceView.Renderer, FrameRenderEventConsumer {
    companion object {
        private const val SCREEN_WIDTH = 256
        private const val SCREEN_HEIGHT = 384

        private val FILTERING_SHADER_MAP = mapOf(
            VideoFiltering.NONE to ShaderProgramSource.NoFilterShader,
            VideoFiltering.LINEAR to ShaderProgramSource.LinearShader,
            VideoFiltering.XBR2 to ShaderProgramSource.XbrShader,
            VideoFiltering.HQ2X to ShaderProgramSource.Hq2xShader,
            VideoFiltering.HQ4X to ShaderProgramSource.Hq4xShader,
            VideoFiltering.QUILEZ to ShaderProgramSource.QuilezShader,
            VideoFiltering.LCD to ShaderProgramSource.LcdShader,
            VideoFiltering.SCANLINES to ShaderProgramSource.ScanlinesShader
        )
    }

    private var nextRenderEvent: FrameRenderEvent? = null
    private var rendererConfiguration: RuntimeRendererConfiguration? = null
    private var mustUpdateConfiguration = false
    private var isBackgroundPositionDirty = false
    private var isBackgroundLoaded = false

    private var backgroundTexture = 0

    private var screenShader: Shader? = null
    private lateinit var backgroundShader: Shader

    private lateinit var posBuffer: FloatBuffer
    private lateinit var uvBuffer: FloatBuffer

    private lateinit var backgroundPosBuffer: FloatBuffer
    private lateinit var backgroundUvBuffer: FloatBuffer

    // Lock used when manipulating background properties
    private val backgroundLock = Any()
    private var background: RuntimeBackground? = null
    private var mustLoadBackground = false
    private var topScreenRect: Rect? = null
    private var bottomScreenRect: Rect? = null

    private var width = 0f
    private var height = 0f

    private var internalWidth = 0
    private var internalHeight = 0

    private var backgroundWidth = 0
    private var backgroundHeight = 0

    // EGL context used to share textures with secondary renderers
    private var eglContext: javax.microedition.khronos.egl.EGLContext? = null

    /** Return the EGL context associated with this renderer when available. */
    fun getSharedEglContext(): javax.microedition.khronos.egl.EGLContext? = eglContext

    fun updateRendererConfiguration(newRendererConfiguration: RuntimeRendererConfiguration?) {
        rendererConfiguration = newRendererConfiguration
        mustUpdateConfiguration = true
    }

    fun updateScreenAreas(topScreenRect: Rect?, bottomScreenRect: Rect?) {
        this.topScreenRect = topScreenRect
        this.bottomScreenRect = bottomScreenRect
        mustUpdateConfiguration = true
        isBackgroundPositionDirty = true
    }

    fun setBackground(background: RuntimeBackground) {
        synchronized(backgroundLock) {
            this.background = background
            mustLoadBackground = true
            isBackgroundPositionDirty = true
            isBackgroundLoaded = false
        }
    }

    override fun prepareNextFrame(frameRenderEvent: FrameRenderEvent) {
        nextRenderEvent = frameRenderEvent
    }

    private fun screenXToViewportX(x: Int): Float {
        return (x / this.width) * 2f - 1f
    }

    private fun screenYToViewportY(y: Int): Float {
        return ((this.height - y) / this.height) * 2f - 1f
    }

    override fun onSurfaceCreated(gl: GL10, config: EGLConfig) {
        // Cache EGL context so other renderers can share textures
        val egl = javax.microedition.khronos.egl.EGLContext.getEGL() as EGL10
        eglContext = egl.eglGetCurrentContext()

        GLES30.glClearColor(0f, 0f, 0f, 1f)
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT or GLES30.GL_DEPTH_BUFFER_BIT)
        GLES30.glDisable(GLES30.GL_CULL_FACE)

        // Setup textures
        val textures = IntArray(1)
        GLES30.glGenTextures(1, textures, 0)
        backgroundTexture = textures[0]

        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, backgroundTexture)
        GLES30.glTexParameterf(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE.toFloat())
        GLES30.glTexParameterf(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE.toFloat())
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)

        // Create background shader
        backgroundShader = ShaderFactory.createShaderProgram(ShaderProgramSource.BackgroundShader)

        applyRendererConfiguration()
        onGlContextReady(EGL14.eglGetCurrentContext().nativeHandle)
    }

    private fun applyRendererConfiguration() {
        internalWidth = SCREEN_WIDTH * (rendererConfiguration?.resolutionScaling ?: 1)
        internalHeight = SCREEN_HEIGHT * (rendererConfiguration?.resolutionScaling ?: 1)

        updateScreenCoordinates()
        updateShader()
    }

    private fun updateScreenCoordinates() {
        val uvs = mutableListOf<Float>()
        val coords = mutableListOf<Float>()

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

        // The texture will have 2 lines between the screens. Take that into account when computing UVs
        val lineRelativeSize = 1 / (SCREEN_HEIGHT + 1).toFloat()
        topScreenRect?.let {
            uvs.add(0f)
            uvs.add(0.5f - lineRelativeSize)

            uvs.add(0f)
            uvs.add(0f)

            uvs.add(1f)
            uvs.add(0f)

            uvs.add(0f)
            uvs.add(0.5f - lineRelativeSize)

            uvs.add(1f)
            uvs.add(0f)

            uvs.add(1f)
            uvs.add(0.5f - lineRelativeSize)

            coords.add(screenXToViewportX(it.x))
            coords.add(screenYToViewportY(it.y + it.height))

            coords.add(screenXToViewportX(it.x))
            coords.add(screenYToViewportY(it.y))

            coords.add(screenXToViewportX(it.x + it.width))
            coords.add(screenYToViewportY(it.y))

            coords.add(screenXToViewportX(it.x))
            coords.add(screenYToViewportY(it.y + it.height))

            coords.add(screenXToViewportX(it.x + it.width))
            coords.add(screenYToViewportY(it.y))

            coords.add(screenXToViewportX(it.x + it.width))
            coords.add(screenYToViewportY(it.y + it.height))
        }
        bottomScreenRect?.let {
            uvs.add(0f)
            uvs.add(1f)

            uvs.add(0f)
            uvs.add(0.5f + lineRelativeSize)

            uvs.add(1f)
            uvs.add(0.5f + lineRelativeSize)

            uvs.add(0f)
            uvs.add(1f)

            uvs.add(1f)
            uvs.add(0.5f + lineRelativeSize)

            uvs.add(1f)
            uvs.add(1f)

            coords.add(screenXToViewportX(it.x))
            coords.add(screenYToViewportY(it.y + it.height))

            coords.add(screenXToViewportX(it.x))
            coords.add(screenYToViewportY(it.y))

            coords.add(screenXToViewportX(it.x + it.width))
            coords.add(screenYToViewportY(it.y))

            coords.add(screenXToViewportX(it.x))
            coords.add(screenYToViewportY(it.y + it.height))

            coords.add(screenXToViewportX(it.x + it.width))
            coords.add(screenYToViewportY(it.y))

            coords.add(screenXToViewportX(it.x + it.width))
            coords.add(screenYToViewportY(it.y + it.height))
        }

        uvBuffer = ByteBuffer.allocateDirect(4 * uvs.size)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer()
                .put(uvs.toFloatArray())

        posBuffer = ByteBuffer.allocateDirect(4 * coords.size)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer()
                .put(coords.toFloatArray())
    }

    private fun updateShader() {
        // Delete previous shader
        screenShader?.delete()

        val shaderSource = FILTERING_SHADER_MAP[rendererConfiguration?.videoFiltering ?: VideoFiltering.NONE] ?: throw Exception("Invalid video filtering")
        screenShader = ShaderFactory.createShaderProgram(shaderSource)
    }

    override fun onSurfaceChanged(gl: GL10, width: Int, height: Int) {
        this.width = width.toFloat()
        this.height = height.toFloat()
        GLES30.glViewport(0, 0, width, height)
        mustUpdateConfiguration = true

        synchronized(backgroundLock) {
            isBackgroundPositionDirty = true
        }
    }

    override fun onDrawFrame(gl: GL10) {
        if (mustUpdateConfiguration) {
            applyRendererConfiguration()
            mustUpdateConfiguration = false
        }

        val currentTextureId = nextRenderEvent?.textureId ?: return

        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT or GLES30.GL_DEPTH_BUFFER_BIT)

        posBuffer.position(0)
        uvBuffer.position(0)

        val indices = posBuffer.capacity() / 2
        screenShader?.let { shader ->
            shader.use()

            GLES30.glEnable(GLES30.GL_DEPTH_TEST)
            GLES30.glDepthFunc(GLES30.GL_NOTEQUAL)
            GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, currentTextureId)
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, shader.textureFiltering)
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, shader.textureFiltering)

            GLES30.glVertexAttribPointer(shader.attribPos, 2, GLES30.GL_FLOAT, false, 0, posBuffer)
            GLES30.glVertexAttribPointer(shader.attribUv, 2, GLES30.GL_FLOAT, false, 0, uvBuffer)
            GLES30.glUniform1i(shader.uniformTex, 0)
            GLES30.glDrawArrays(GLES30.GL_TRIANGLES, 0, indices)
        }

        synchronized(backgroundLock) {
            renderBackground()
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

            val uvs = arrayOf(
                0f, 1f,
                0f, 0f,
                1f, 0f,
                0f, 1f,
                1f, 0f,
                1f, 1f
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
        val background = background ?: return
        val coords = getBackgroundCoords(background.mode, backgroundWidth, backgroundHeight)

        backgroundPosBuffer = ByteBuffer.allocateDirect(4 * coords.size)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer()
                .put(coords.toFloatArray())

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