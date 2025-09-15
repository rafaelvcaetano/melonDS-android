package me.magnum.melonds.domain.model.render

data class FrameRenderEvent(
    val isValidFrame: Boolean,
    val textureId: Int,
    val renderFenceHandle: Long,
    val presentFenceHandle: Long,
)
