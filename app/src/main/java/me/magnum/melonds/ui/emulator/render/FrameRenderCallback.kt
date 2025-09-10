package me.magnum.melonds.ui.emulator.render

typealias PresentFaceHandle = Long

fun interface FrameRenderCallback {
    fun renderFrame(isValidFrame: Boolean, frameTextureId: Int, renderFenceHandle: Long): PresentFaceHandle
}