package me.magnum.melonds.domain.model

import me.magnum.melonds.domain.model.render.RenderStrategy

data class RendererConfiguration(
    val renderer: VideoRenderer,
    val videoFiltering: VideoFiltering,
    val threadedRendering: Boolean,
    val renderStrategy: RenderStrategy,
    private val internalResolutionScaling: Int,
) {

    val resolutionScaling get() = when (renderer) {
        VideoRenderer.SOFTWARE -> 1
        VideoRenderer.OPENGL -> internalResolutionScaling
        VideoRenderer.COMPUTE -> internalResolutionScaling
    }
}