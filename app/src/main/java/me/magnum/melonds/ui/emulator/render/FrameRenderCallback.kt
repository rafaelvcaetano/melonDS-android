package me.magnum.melonds.ui.emulator.render

fun interface FrameRenderCallback {
    fun renderFrame(isValidFrame: Boolean, frameTextureId: Int)
}