package me.magnum.melonds.ui.emulator

import android.content.Context
import android.graphics.BitmapFactory
import android.opengl.EGL14
import android.opengl.GLES20
import android.opengl.GLES30
import android.opengl.GLSurfaceView
import android.opengl.GLUtils
import android.opengl.Matrix
import me.magnum.melonds.common.opengl.Shader
import me.magnum.melonds.common.opengl.ShaderFactory
import me.magnum.melonds.common.opengl.ShaderProgramSource
import me.magnum.melonds.domain.model.Rect
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
) : GLSurfaceView.Renderer {
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

    private val renderEventQueue = LinkedList<FrameRenderEvent>()
    private var rendererConfiguration: RuntimeRendererConfiguration? = null
    private var mustUpdateConfiguration = false
    private var isBackgroundPositionDirty = false
    private var isBackgroundLoaded = false

    private var backgroundTexture = 0

    private var screenShader: Shader? = null
    private lateinit var backgroundShader: Shader

    private lateinit var mvpMatrix: FloatArray
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

    fun prepareNextFrame(frameRenderEvent: FrameRenderEvent) {
        synchronized(renderEventQueue) {
            renderEventQueue.add(frameRenderEvent)
        }
    }

    private fun screenXToViewportX(x: Int): Float {
        return (x / this.width) * 2f - 1f
    }

    private fun screenYToViewportY(y: Int): Float {
        return ((this.height - y) / this.height) * 2f - 1f
    }

    override fun onSurfaceCreated(gl: GL10, config: EGLConfig) {
        GLES20.glClearColor(0f, 0f, 0f, 1f)
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
        GLES20.glDisable(GLES20.GL_CULL_FACE)

        // Setup textures
        val textures = IntArray(1)
        GLES20.glGenTextures(1, textures, 0)
        backgroundTexture = textures[0]

        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, backgroundTexture)
        GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE.toFloat())
        GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE.toFloat())
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)

        // Create background shader
        backgroundShader = ShaderFactory.createShaderProgram(ShaderProgramSource.BackgroundShader)

        // Create MVP matrix
        mvpMatrix = FloatArray(16)
        val projectionMatrix = FloatArray(16)
        val viewMatrix = FloatArray(16)
        Matrix.orthoM(projectionMatrix, 0, -1f, 1f, -1f, 1f, -1f, 10f)
        Matrix.setLookAtM(viewMatrix, 0, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 1f, 0f)
        Matrix.multiplyMM(mvpMatrix, 0, projectionMatrix, 0, viewMatrix, 0)

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
        GLES20.glViewport(0, 0, width, height)
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

        val currentGlFenceSync: Long
        val currentTextureId: Int
        synchronized(renderEventQueue) {
            val renderEvent = renderEventQueue.removeLastOrNull() ?: return
            currentGlFenceSync = renderEvent.glSyncFence
            currentTextureId = renderEvent.textureId

            while (renderEventQueue.isNotEmpty()) {
                // Discard old events
                val discardedEvent = renderEventQueue.removeLast()
                GLES30.glDeleteSync(discardedEvent.glSyncFence)
            }
        }

        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)

        synchronized(backgroundLock) {
            renderBackground()
        }

        posBuffer.position(0)
        uvBuffer.position(0)

        val indices = posBuffer.capacity() / 2
        screenShader?.let { shader ->
            shader.use()

            GLES30.glWaitSync(currentGlFenceSync, GLES30.GL_SYNC_FLUSH_COMMANDS_BIT, 100_000_000)
            GLES30.glDeleteSync(currentGlFenceSync)

            GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, currentTextureId)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, shader.textureFiltering)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, shader.textureFiltering)

            GLES20.glUniformMatrix4fv(shader.uniformMvp, 1, false, mvpMatrix, 0)
            GLES20.glVertexAttribPointer(shader.attribPos, 2, GLES20.GL_FLOAT, false, 0, posBuffer)
            GLES20.glVertexAttribPointer(shader.attribUv, 2, GLES20.GL_FLOAT, false, 0, uvBuffer)
            GLES20.glUniform1i(shader.uniformTex, 0)
            GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, indices)
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
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, backgroundTexture)
        GLES20.glUniformMatrix4fv(backgroundShader.uniformMvp, 1, false, mvpMatrix, 0)
        GLES20.glVertexAttribPointer(backgroundShader.attribPos, 2, GLES20.GL_FLOAT, false, 0, backgroundPosBuffer)
        GLES20.glVertexAttribPointer(backgroundShader.attribUv, 2, GLES20.GL_FLOAT, false, 0, backgroundUvBuffer)
        GLES20.glUniform1i(backgroundShader.uniformTex, 0)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, indices)
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

            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, backgroundTexture)
            GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap, 0)
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