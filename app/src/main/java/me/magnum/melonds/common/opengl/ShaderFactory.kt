package me.magnum.melonds.common.opengl

import android.opengl.GLES20

object ShaderFactory {
    private const val DEFAULT_VERT_SHADER = "uniform mat4 MVP;\n" +
            "attribute vec2 vUV;\n" +
            "attribute vec2 vPos;\n" +
            "varying vec2 uv;\n" +
            "void main()\n" +
            "{\n" +
            "    gl_Position = MVP * vec4(vPos, 0.0, 1.0);\n" +
            "    uv = vUV;\n" +
            "}\n"

    private const val DEFAULT_FRAG_SHADER = "precision mediump float;\n" +
            "uniform sampler2D tex;\n" +
            "varying vec2 uv;\n" +
            "void main()\n" +
            "{\n" +
            "    vec4 color = texture2D(tex, uv);\n" +
            "    //float color = texture2D(tex, uv).a;\n" +
            "    gl_FragColor = vec4(color.bgr, 1);\n" +
            "}\n"

    private const val DEFAULT_BACKGROUND_FRAG_SHADER = "precision mediump float;\n" +
            "uniform sampler2D tex;\n" +
            "varying vec2 uv;\n" +
            "void main()\n" +
            "{\n" +
            "    vec4 color = texture2D(tex, uv);\n" +
            "    gl_FragColor = vec4(color.rgb, 1);\n" +
            "}\n"

    fun createDefaultShaderProgram(): Shader {
        return createShaderProgram(DEFAULT_VERT_SHADER, DEFAULT_FRAG_SHADER)
    }

    fun createDefaultBackgroundShaderProgram(): Shader {
        return createShaderProgram(DEFAULT_VERT_SHADER, DEFAULT_BACKGROUND_FRAG_SHADER)
    }

    private fun createShaderProgram(vertexShader: String, fragmentShader: String): Shader {
        val program = GLES20.glCreateProgram()
        GLES20.glAttachShader(program, createShader(GLES20.GL_VERTEX_SHADER, vertexShader))
        GLES20.glAttachShader(program, createShader(GLES20.GL_FRAGMENT_SHADER, fragmentShader))
        GLES20.glLinkProgram(program)
        val result = IntArray(1)
        GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, result, 0)

        if (result[0] == GLES20.GL_FALSE) {
            System.err.println(GLES20.glGetProgramInfoLog(program))
        }

        return Shader(program)
    }

    private fun createShader(shaderType: Int, code: String): Int {
        val shader = GLES20.glCreateShader(shaderType)
        GLES20.glShaderSource(shader, code)
        GLES20.glCompileShader(shader)
        val result = IntArray(1)
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, result, 0)

        if (result[0] == GLES20.GL_FALSE) {
            System.err.println(GLES20.glGetShaderInfoLog(shader))
        }

        return shader
    }
}