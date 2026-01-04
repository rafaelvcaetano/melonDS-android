package me.magnum.melonds.domain.model

import me.magnum.melonds.common.opengl.ShaderProgramSource

data class RendererConfiguration(
    val renderer: VideoRenderer,
    val videoFiltering: VideoFiltering,
    val threadedRendering: Boolean,
    private val internalResolutionScaling: Int,
    val conservativeCoverageEnabled: Boolean,
    val conservativeCoveragePx: Float,
    val conservativeCoverageDepthBias: Float,
    val conservativeCoverageApplyRepeat: Boolean,
    val conservativeCoverageApplyClamp: Boolean,
    val debug3dClearMagenta: Boolean,
    val customShader: ShaderProgramSource?,
) {

    val resolutionScaling get() = when (renderer) {
        VideoRenderer.SOFTWARE -> 1
        VideoRenderer.OPENGL -> internalResolutionScaling
    }
}
