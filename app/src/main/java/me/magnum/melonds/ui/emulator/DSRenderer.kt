package me.magnum.melonds.ui.emulator

import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import android.util.Log
import me.magnum.melonds.domain.model.Rect
import me.magnum.melonds.domain.model.RendererConfiguration
import me.magnum.melonds.domain.model.VideoFiltering
import me.magnum.melonds.utils.ShaderUtils.createDefaultShaderProgram
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

class DSRenderer(private var rendererConfiguration: RendererConfiguration) : GLSurfaceView.Renderer {
    companion object {
        private const val TAG = "DSRenderer"
        private const val SCREEN_WIDTH = 256
        private const val SCREEN_HEIGHT = 384
    }

    private var rendererListener: RendererListener? = null
    private var mustUpdateConfiguration = false

    private var mainTexture = 0
    private var program = 0
    private var l_MVP = 0
    private var l_uv = 0
    private var l_pos = 0
    private var l_tex = 0

    private lateinit var mvpMatrix: FloatArray
    private lateinit var posBuffer: FloatBuffer
    private lateinit var uvBuffer: FloatBuffer
    private lateinit var texBuffer: ByteBuffer

    private var topScreenRect: Rect? = null
    private var bottomScreenRect: Rect? = null

    private var width = 0f
    private var height = 0f

    fun setRendererListener(listener: RendererListener?) {
        rendererListener = listener
    }

    fun updateRendererConfiguration(newRendererConfiguration: RendererConfiguration) {
        rendererConfiguration = newRendererConfiguration
        mustUpdateConfiguration = true
    }

    fun updateScreenAreas(topScreenRect: Rect?, bottomScreenRect: Rect?) {
        this.topScreenRect = topScreenRect
        this.bottomScreenRect = bottomScreenRect
        mustUpdateConfiguration = true
    }

    private fun screenXToViewportX(x: Int): Float {
        return (x / this.width) * 2f - 1f
    }

    private fun screenYToViewportY(y: Int): Float {
        return ((this.height - y) / this.height) * 2f - 1f
    }

    override fun onSurfaceCreated(gl: GL10, config: EGLConfig) {
        if (rendererListener == null) {
            Log.w(TAG, "No frame buffer updater specified")
            return
        }

        GLES20.glClearColor(0f, 0f, 0f, 1f)
        GLES20.glDisable(GLES20.GL_CULL_FACE)

        // Setup main texture
        val texture = IntArray(1)
        GLES20.glGenTextures(1, texture, 0)
        mainTexture = texture[0]
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, mainTexture)
        GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE.toFloat())
        GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE.toFloat())

        // Create shader
        program = createDefaultShaderProgram()
        GLES20.glUseProgram(program)
        l_MVP = GLES20.glGetUniformLocation(program, "MVP")
        l_uv = GLES20.glGetAttribLocation(program, "vUV")
        l_pos = GLES20.glGetAttribLocation(program, "vPos")
        l_tex = GLES20.glGetUniformLocation(program, "tex")
        GLES20.glEnableVertexAttribArray(l_MVP)
        GLES20.glEnableVertexAttribArray(l_uv)
        GLES20.glEnableVertexAttribArray(l_pos)
        GLES20.glEnableVertexAttribArray(l_tex)

        // Create MVP matrix
        mvpMatrix = FloatArray(16)
        val projectionMatrix = FloatArray(16)
        val viewMatrix = FloatArray(16)
        Matrix.orthoM(projectionMatrix, 0, -1f, 1f, -1f, 1f, -1f, 10f)
        Matrix.setLookAtM(viewMatrix, 0, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 1f, 0f)
        Matrix.multiplyMM(mvpMatrix, 0, projectionMatrix, 0, viewMatrix, 0)

        texBuffer = ByteBuffer.allocateDirect(SCREEN_WIDTH * SCREEN_HEIGHT * 4)
                .order(ByteOrder.nativeOrder())

        applyRendererConfiguration()
    }

    private fun applyRendererConfiguration() {
        updateScreenCoordinates()
        updateTextureParameters()
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

        topScreenRect?.let {
            uvs.add(0f)
            uvs.add(0.5f)

            uvs.add(0f)
            uvs.add(0f)

            uvs.add(1f)
            uvs.add(0f)

            uvs.add(0f)
            uvs.add(0.5f)

            uvs.add(1f)
            uvs.add(0f)

            uvs.add(1f)
            uvs.add(0.5f)

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
            uvs.add(0.5f)

            uvs.add(1f)
            uvs.add(0.5f)

            uvs.add(0f)
            uvs.add(1f)

            uvs.add(1f)
            uvs.add(0.5f)

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

    private fun updateTextureParameters() {
        val filtering = when (rendererConfiguration.videoFiltering) {
            VideoFiltering.NONE -> GLES20.GL_NEAREST
            VideoFiltering.LINEAR -> GLES20.GL_LINEAR
            else -> GLES20.GL_LINEAR
        }

        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, mainTexture)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, filtering)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, filtering)
    }

    override fun onSurfaceChanged(gl: GL10, width: Int, height: Int) {
        this.width = width.toFloat()
        this.height = height.toFloat()
        GLES20.glViewport(0, 0, width, height)
        mustUpdateConfiguration = true
    }

    override fun onDrawFrame(gl: GL10) {
        if (mustUpdateConfiguration) {
            applyRendererConfiguration()
            mustUpdateConfiguration = false
        }

        rendererListener?.updateFrameBuffer(texBuffer)
        posBuffer.position(0)
        uvBuffer.position(0)
        texBuffer.position(0)

        val indices = posBuffer.capacity() / 2
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, mainTexture)
        GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, SCREEN_WIDTH, SCREEN_HEIGHT, 0, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, texBuffer)
        GLES20.glUniformMatrix4fv(l_MVP, 1, false, mvpMatrix, 0)
        GLES20.glVertexAttribPointer(l_pos, 2, GLES20.GL_FLOAT, false, 2 * 4, posBuffer)
        GLES20.glVertexAttribPointer(l_uv, 2, GLES20.GL_FLOAT, false, 2 * 4, uvBuffer)
        GLES20.glUniform1i(l_tex, 0)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, indices)
    }

    interface RendererListener {
        fun updateFrameBuffer(dst: ByteBuffer)
    }
}