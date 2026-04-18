package me.magnum.melonds.impl.dtos.rom

import androidx.core.net.toUri
import com.google.gson.annotations.SerializedName
import me.magnum.melonds.domain.model.rom.config.RomGbaSlotConfig

/**
 * GBA slot config DTO holds information about all possible configs in a single object. This makes (de)serialization easier since it avoids dealing with polymorphism.
 */
data class RomGbaSlotConfigDto(
    @SerializedName("type")
    val type: Type,
    @SerializedName("gbaRomPath")
    val gbaRomPath: String?,
    @SerializedName("gbaSavePath")
    val gbaSavePath: String?,
) {

    enum class Type {
        None, GbaRom, RumblePak, MemoryExpansion
    }

    fun toModel(): RomGbaSlotConfig {
        return when (type) {
            Type.None -> RomGbaSlotConfig.None
            Type.GbaRom -> RomGbaSlotConfig.GbaRom(
                romPath = gbaRomPath?.toUri(),
                savePath = gbaSavePath?.toUri(),
            )
            Type.RumblePak -> RomGbaSlotConfig.RumblePak
            Type.MemoryExpansion -> RomGbaSlotConfig.MemoryExpansion
        }
    }

    companion object {
        fun fromModel(romGbaSlotConfig: RomGbaSlotConfig): RomGbaSlotConfigDto {
            return RomGbaSlotConfigDto(
                type = romGbaSlotConfig.dtoType(),
                gbaRomPath = (romGbaSlotConfig as? RomGbaSlotConfig.GbaRom)?.romPath?.toString(),
                gbaSavePath = (romGbaSlotConfig as? RomGbaSlotConfig.GbaRom)?.savePath?.toString(),
            )
        }

        private fun RomGbaSlotConfig.dtoType(): Type {
            return when (this) {
                RomGbaSlotConfig.None -> Type.None
                is RomGbaSlotConfig.GbaRom -> Type.GbaRom
                RomGbaSlotConfig.RumblePak -> Type.RumblePak
                RomGbaSlotConfig.MemoryExpansion -> Type.MemoryExpansion
            }
        }
    }
}