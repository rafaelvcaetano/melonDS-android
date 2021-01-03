package me.magnum.melonds.domain.model

data class EmulatorConfiguration(val configDirectory: String, val useJit: Boolean, val rendererConfiguration: RendererConfiguration)