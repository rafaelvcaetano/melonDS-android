package me.magnum.melonds.ui.emulator.model

import me.magnum.melonds.domain.model.VideoFiltering

data class RuntimeRendererConfiguration(
    val videoFiltering: VideoFiltering,
    val resolutionScaling: Int,
)