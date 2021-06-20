package me.magnum.melonds.common.opengl

import android.opengl.GLES20

class Shader(private val programId: Int) {
    val uniformMvp: Int
    val attribUv: Int
    val attribPos: Int
    val uniformTex: Int

    init {
        GLES20.glUseProgram(programId)
        uniformMvp = GLES20.glGetUniformLocation(programId, "MVP")
        attribUv = GLES20.glGetAttribLocation(programId, "vUV")
        attribPos = GLES20.glGetAttribLocation(programId, "vPos")
        uniformTex = GLES20.glGetUniformLocation(programId, "tex")
        GLES20.glUseProgram(0)
    }

    fun use() {
        GLES20.glUseProgram(programId)
        GLES20.glEnableVertexAttribArray(attribUv)
        GLES20.glEnableVertexAttribArray(attribPos)
    }

    fun delete() {
        GLES20.glDeleteProgram(programId)
    }
}