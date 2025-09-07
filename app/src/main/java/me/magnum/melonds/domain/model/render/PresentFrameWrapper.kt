package me.magnum.melonds.domain.model.render

data class PresentFrameWrapper(
    var isValidFrame: Boolean = false,
    var textureId: Int = 0,
    var renderFenceHandle: Long = 0,
    var presentFenceHandle: Long = 0,
)