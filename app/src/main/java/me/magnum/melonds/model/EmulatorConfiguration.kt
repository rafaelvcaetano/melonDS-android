package me.magnum.melonds.model

data class EmulatorConfiguration(val configDirectory: String, val useJit: Boolean, val rendererConfiguration: RendererConfiguration)