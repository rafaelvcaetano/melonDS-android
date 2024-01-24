package me.magnum.melonds.domain.model

data class RendererConfiguration(
    val renderer: VideoRenderer,
    private val internalVideoFiltering: VideoFiltering,
    val threadedRendering: Boolean,
    private val internalResolutionScaling: Int,
) {

    val videoFiltering get() = when (renderer) {
        VideoRenderer.SOFTWARE -> internalVideoFiltering
        VideoRenderer.OPENGL -> VideoFiltering.NONE
    }

    val resolutionScaling get() = when (renderer) {
        VideoRenderer.SOFTWARE -> 1
        VideoRenderer.OPENGL -> internalResolutionScaling
    }
}