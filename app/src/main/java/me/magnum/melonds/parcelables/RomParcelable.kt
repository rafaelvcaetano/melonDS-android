package me.magnum.melonds.parcelables

import android.net.Uri
import android.os.Parcel
import android.os.Parcelable
import me.magnum.melonds.model.Rom
import me.magnum.melonds.model.RomConfig
import java.util.*

class RomParcelable : Parcelable {
    var rom: Rom
        private set

    constructor(rom: Rom) {
        this.rom = rom
    }

    private constructor(parcel: Parcel) {
        val romConfig = RomConfig()
        rom = Rom(parcel.readString()!!, Uri.parse(parcel.readString()), romConfig, parcel.readLong().let { if (it == (-1).toLong()) null else Date(it) })
        romConfig.setLoadGbaCart(parcel.readInt() == 1)
        romConfig.gbaCartPath = parcel.readString()?.let { Uri.parse(it) }
        romConfig.gbaSavePath = parcel.readString()?.let { Uri.parse(it) }
    }

    override fun writeToParcel(dest: Parcel, flags: Int) {
        dest.writeString(rom.name)
        dest.writeString(rom.uri.toString())
        dest.writeLong(rom.lastPlayed?.time ?: -1)
        dest.writeInt(if (rom.config.loadGbaCart()) 1 else 0)
        dest.writeString(rom.config.gbaCartPath?.toString())
        dest.writeString(rom.config.gbaSavePath?.toString())
    }

    override fun describeContents(): Int {
        return 0
    }

    companion object CREATOR : Parcelable.Creator<RomParcelable> {
        override fun createFromParcel(parcel: Parcel): RomParcelable {
            return RomParcelable(parcel)
        }

        override fun newArray(size: Int): Array<RomParcelable?> {
            return arrayOfNulls(size)
        }
    }
}