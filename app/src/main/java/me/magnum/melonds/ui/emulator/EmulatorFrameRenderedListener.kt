package me.magnum.melonds.ui.emulator

fun interface EmulatorFrameRenderedListener {
    fun onFrameRendered(glFenceSync: Long, textureId: Int)
}