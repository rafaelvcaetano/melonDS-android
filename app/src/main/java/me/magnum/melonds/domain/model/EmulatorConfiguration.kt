package me.magnum.melonds.domain.model

data class EmulatorConfiguration(
        val useCustomBios: Boolean,
        val dsConfigDirectory: String,
        val dsiConfigDirectory: String,
        val fastForwardSpeedMultiplier: Float,
        val useJit: Boolean,
        val consoleType: ConsoleType,
        val soundEnabled: Boolean,
        val micSource: MicSource,
        val firmwareConfiguration: FirmwareConfiguration,
        val rendererConfiguration: RendererConfiguration
)