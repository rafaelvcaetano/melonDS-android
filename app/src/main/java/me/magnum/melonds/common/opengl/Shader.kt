package me.magnum.melonds.common.opengl

import android.opengl.GLES30

private const val INVALID_ATTRIBUTE = -1

class Shader(
    private val vertexShaderId: Int,
    private val fragmentShaderId: Int,
    private val programId: Int,
    val textureFiltering: Int,
    private val bindings: ShaderProgramSource.Bindings,
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
        attribUv = getAttributeLocation(bindings.attribUv)
        attribPos = getAttributeLocation(bindings.attribPos)
        attribAlpha = getAttributeLocation(bindings.attribAlpha)
        uniformTex = getUniformLocation(bindings.uniformTex)
        uniformPrevTex = getUniformLocation(bindings.uniformPrevTex)
        uniformPrevWeight = getUniformLocation(bindings.uniformPrevWeight)
        uniformTexSize = getUniformLocation(bindings.uniformTexSize)
        uniformViewportSize = getUniformLocation(bindings.uniformViewportSize)
        uniformUvBounds = getUniformLocation(bindings.uniformScreenUvBounds)
        GLES30.glUseProgram(0)
    }

    fun use() {
        GLES30.glUseProgram(programId)
        if (attribUv != INVALID_ATTRIBUTE) {
            GLES30.glEnableVertexAttribArray(attribUv)
        }
        if (attribPos != INVALID_ATTRIBUTE) {
            GLES30.glEnableVertexAttribArray(attribPos)
        }
        if (attribAlpha != INVALID_ATTRIBUTE) {
            GLES30.glEnableVertexAttribArray(attribAlpha)
        }
    }

    fun delete() {
        GLES30.glDeleteShader(vertexShaderId)
        GLES30.glDeleteShader(fragmentShaderId)
        GLES30.glDeleteProgram(programId)
    }

    private fun getAttributeLocation(name: String): Int {
        if (name.isBlank()) {
            return INVALID_ATTRIBUTE
        }
        return GLES30.glGetAttribLocation(programId, name)
    }

    private fun getUniformLocation(name: String): Int {
        if (name.isBlank()) {
            return INVALID_ATTRIBUTE
        }
        return GLES30.glGetUniformLocation(programId, name)
    }
}