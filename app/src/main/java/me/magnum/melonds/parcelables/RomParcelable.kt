package me.magnum.melonds.parcelables

import android.net.Uri
import android.os.Parcel
import android.os.Parcelable
import me.magnum.melonds.domain.model.Rom
import java.util.*

class RomParcelable : Parcelable {
    var rom: Rom
        private set

    constructor(rom: Rom) {
        this.rom = rom
    }

    private constructor(parcel: Parcel) {
        val name = parcel.readString()
        val uri = Uri.parse(parcel.readString())
        val lastPlayed = parcel.readLong().let { if (it == (-1).toLong()) null else Date(it) }
        val romConfig = parcel.readParcelable<RomConfigParcelable>(RomConfigParcelable::class.java.classLoader)
        rom = Rom(name!!, uri, romConfig!!.romConfig, lastPlayed)
    }

    override fun writeToParcel(dest: Parcel, flags: Int) {
        dest.writeString(rom.name)
        dest.writeString(rom.uri.toString())
        dest.writeLong(rom.lastPlayed?.time ?: -1)
        dest.writeParcelable(RomConfigParcelable(rom.config), 0)
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