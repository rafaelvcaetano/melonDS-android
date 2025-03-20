package me.magnum.melonds.ui.emulator

fun interface EmulatorFrameRenderedListener {
    fun onFrameRendered(textureId: Int)
}