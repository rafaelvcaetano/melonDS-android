package me.magnum.melonds.common.opengl

import me.magnum.melonds.domain.model.VideoFiltering

object VideoFilterShaderProvider {
    private val FILTERING_SHADER_MAP = mapOf(
        VideoFiltering.NONE to ShaderProgramSource.NoFilterShader,
        VideoFiltering.LINEAR to ShaderProgramSource.LinearShader,
        VideoFiltering.XBR2 to ShaderProgramSource.XbrShader,
        VideoFiltering.HQ2X to ShaderProgramSource.Hq2xShader,
        VideoFiltering.HQ4X to ShaderProgramSource.Hq4xShader,
        VideoFiltering.QUILEZ to ShaderProgramSource.QuilezShader,
        VideoFiltering.LCD to ShaderProgramSource.LcdShader,
        VideoFiltering.SCANLINES to ShaderProgramSource.ScanlinesShader,
    )

    fun getShaderSource(filtering: VideoFiltering): ShaderProgramSource =
        FILTERING_SHADER_MAP[filtering] ?: ShaderProgramSource.NoFilterShader
}