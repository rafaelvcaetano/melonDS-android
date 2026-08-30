package me.magnum.melonds.parcelables

import android.os.Parcel
import android.os.Parcelable
import androidx.core.net.toUri
import me.magnum.melonds.domain.model.rom.config.RomGbaSlotConfig

class RomGbaSlotConfigParcelable : Parcelable {
    val gbaSlotConfig: RomGbaSlotConfig

    constructor(gbaSlotConfig: RomGbaSlotConfig) {
        this.gbaSlotConfig = gbaSlotConfig
    }

    private constructor(parcel: Parcel) {
        val type = parcel.readInt()
        gbaSlotConfig = when (type) {
            TYPE_NONE -> RomGbaSlotConfig.None
            TYPE_GBA_ROM -> RomGbaSlotConfig.GbaRom(parcel.readString()?.toUri(), parcel.readString()?.toUri())
            TYPE_RUMBLE_PAK -> RomGbaSlotConfig.RumblePak
            TYPE_MEMORY_EXPANSION -> RomGbaSlotConfig.MemoryExpansion
            TYPE_MOTION_PAK_HOMEBREW -> RomGbaSlotConfig.MotionPakHomebrew
            TYPE_MOTION_PAK_RETAIL -> RomGbaSlotConfig.MotionPakRetail
            else -> throw UnsupportedOperationException("Unsupported GBA slot type: $type")
        }
    }

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        when (gbaSlotConfig) {
            is RomGbaSlotConfig.None -> parcel.writeInt(TYPE_NONE)
            is RomGbaSlotConfig.GbaRom -> {
                parcel.writeInt(TYPE_GBA_ROM)
                parcel.writeString(gbaSlotConfig.romPath?.toString())
                parcel.writeString(gbaSlotConfig.savePath?.toString())
            }
            RomGbaSlotConfig.RumblePak -> parcel.writeInt(TYPE_RUMBLE_PAK)
            is RomGbaSlotConfig.MemoryExpansion -> parcel.writeInt(TYPE_MEMORY_EXPANSION)
            RomGbaSlotConfig.MotionPakHomebrew -> parcel.writeInt(TYPE_MOTION_PAK_HOMEBREW)
            RomGbaSlotConfig.MotionPakRetail -> parcel.writeInt(TYPE_MOTION_PAK_RETAIL)
        }
    }

    override fun describeContents(): Int {
        return 0
    }

    companion object CREATOR : Parcelable.Creator<RomGbaSlotConfigParcelable> {
        private const val TYPE_NONE = 0
        private const val TYPE_GBA_ROM = 1
        private const val TYPE_MEMORY_EXPANSION = 2
        private const val TYPE_RUMBLE_PAK = 3
        private const val TYPE_MOTION_PAK_HOMEBREW = 4
        private const val TYPE_MOTION_PAK_RETAIL = 5

        override fun createFromParcel(parcel: Parcel): RomGbaSlotConfigParcelable {
            return RomGbaSlotConfigParcelable(parcel)
        }

        override fun newArray(size: Int): Array<RomGbaSlotConfigParcelable?> {
            return arrayOfNulls(size)
        }
    }
}