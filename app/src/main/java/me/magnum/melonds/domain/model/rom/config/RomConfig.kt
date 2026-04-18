package me.magnum.melonds.domain.model.rom.config

import java.util.*

data class RomConfig(
    val runtimeConsoleType: RuntimeConsoleType = RuntimeConsoleType.DEFAULT,
    val runtimeMicSource: RuntimeMicSource = RuntimeMicSource.DEFAULT,
    val layoutId: UUID? = null,
    val gbaSlotConfig: RomGbaSlotConfig = RomGbaSlotConfig.None,
    val customName: String? = null,
) {

    companion object {
        fun default() = RomConfig()

        fun forDsiWareTitle(): RomConfig {
            return RomConfig(
                runtimeConsoleType = RuntimeConsoleType.DSi,
                runtimeMicSource = RuntimeMicSource.DEFAULT,
                layoutId = null,
                gbaSlotConfig = RomGbaSlotConfig.None,
                customName = null,
            )
        }
    }
}
