package me.magnum.melonds.domain.model.render

data class FrameRenderEvent(
    val glSyncFence: Long,
    val textureId: Int,
)