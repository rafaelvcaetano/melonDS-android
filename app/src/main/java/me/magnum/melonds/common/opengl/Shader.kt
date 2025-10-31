package me.magnum.melonds.common.opengl

import android.opengl.GLES30

private const val INVALID_ATTRIBUTE = -1

class Shader(
    private val vertexShaderId: Int,
    private val fragmentShaderId: Int,
    private val programId: Int,
    val textureFiltering: Int,
) {
    val attribUv: Int
    val attribPos: Int
    val attribAlpha: Int
    val uniformTex: Int
    val uniformPrevTex: Int
    val uniformPrevWeight: Int
    val uniformTexSize: Int
    val uniformViewportSize: Int
    val uniformUvBounds: Int

    init {
        GLES30.glUseProgram(programId)
        attribUv = GLES30.glGetAttribLocation(programId, "vUV")
        attribPos = GLES30.glGetAttribLocation(programId, "vPos")
        attribAlpha = GLES30.glGetAttribLocation(programId, "vAlpha")
        uniformTex = GLES30.glGetUniformLocation(programId, "tex")
        uniformPrevTex = GLES30.glGetUniformLocation(programId, "prevTex")
        uniformPrevWeight = GLES30.glGetUniformLocation(programId, "responseWeight")
        uniformTexSize = GLES30.glGetUniformLocation(programId, "texSize")
        uniformViewportSize = GLES30.glGetUniformLocation(programId, "viewportSize")
        uniformUvBounds = GLES30.glGetUniformLocation(programId, "screenUvBounds")
        GLES30.glUseProgram(0)
    }

    fun use() {
        GLES30.glUseProgram(programId)
        GLES30.glEnableVertexAttribArray(attribUv)
        GLES30.glEnableVertexAttribArray(attribPos)
        if (attribAlpha != INVALID_ATTRIBUTE) {
            GLES30.glEnableVertexAttribArray(attribAlpha)
        }
    }

    fun delete() {
        GLES30.glDeleteShader(vertexShaderId)
        GLES30.glDeleteShader(fragmentShaderId)
        GLES30.glDeleteProgram(programId)
    }
}