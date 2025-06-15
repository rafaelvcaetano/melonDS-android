package me.magnum.melonds.ui.emulator

import me.magnum.melonds.domain.model.render.FrameRenderEvent

interface FrameRenderEventConsumer {
    fun prepareNextFrame(frameRenderEvent: FrameRenderEvent)
}
