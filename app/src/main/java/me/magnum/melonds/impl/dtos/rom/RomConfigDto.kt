package me.magnum.melonds.impl.dtos.rom

import com.google.gson.annotations.SerializedName
import me.magnum.melonds.domain.model.DsExternalScreen
import me.magnum.melonds.domain.model.rom.config.RomConfig
import me.magnum.melonds.domain.model.rom.config.RuntimeConsoleType
import me.magnum.melonds.domain.model.rom.config.RuntimeMicSource
import java.util.UUID

data class RomConfigDto(
    @SerializedName("runtimeConsoleType")
    val runtimeConsoleType: RuntimeConsoleType,
    @SerializedName("runtimeMicSource")
    val runtimeMicSource: RuntimeMicSource,
    @SerializedName("layoutId")
    val layoutId: String?,
    @SerializedName("externalLayoutId")
    val externalLayoutId: String?,
    @SerializedName("externalScreen")
    val externalScreen: String?,
    @SerializedName("gbaSlotConfig")
    val gbaSlotConfig: RomGbaSlotConfigDto,
    @SerializedName("customName")
    val customName: String? = null,
) {

    companion object {
        fun fromModel(romConfig: RomConfig): RomConfigDto {
            return RomConfigDto(
                romConfig.runtimeConsoleType,
                romConfig.runtimeMicSource,
                romConfig.layoutId?.toString(),
                romConfig.externalLayoutId?.toString(),
                romConfig.externalScreen?.name?.lowercase(),
                RomGbaSlotConfigDto.fromModel(romConfig.gbaSlotConfig),
                romConfig.customName,
            )
        }
    }

    fun toModel(): RomConfig {
        return RomConfig(
            runtimeConsoleType,
            runtimeMicSource,
            layoutId?.let { UUID.fromString(it) },
            externalLayoutId?.let { UUID.fromString(it) },
            externalScreen?.let { DsExternalScreen.valueOf(it.uppercase()) },
            gbaSlotConfig.toModel(),
            customName = customName,
        )
    }
}