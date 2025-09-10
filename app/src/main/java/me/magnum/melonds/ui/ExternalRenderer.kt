package me.magnum.melonds.ui

import me.magnum.melonds.domain.model.VideoFiltering
import me.magnum.melonds.ui.emulator.FrameRenderEventConsumer

interface ExternalRenderer : FrameRenderEventConsumer {
    fun updateVideoFiltering(videoFiltering: VideoFiltering)
}