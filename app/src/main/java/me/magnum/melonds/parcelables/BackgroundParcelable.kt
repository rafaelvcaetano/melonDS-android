package me.magnum.melonds.parcelables

import android.os.Parcel
import android.os.Parcelable
import androidx.core.net.toUri
import me.magnum.melonds.domain.model.Background
import me.magnum.melonds.domain.model.Orientation
import java.util.*

class BackgroundParcelable : Parcelable {
    var background: Background
        private set

    constructor(background: Background) {
        this.background = background
    }

    private constructor(parcel: Parcel) {
        val id = parcel.readString()?.let { UUID.fromString(it) }
        val name = parcel.readString() ?: throw NullPointerException("Missing name string")
        val orientation = Orientation.entries[parcel.readInt()]
        val uri = parcel.readString()!!.toUri()
        background = Background(id, name, orientation, uri)
    }

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeString(background.id?.toString())
        parcel.writeString(background.name)
        parcel.writeInt(background.orientation.ordinal)
        parcel.writeString(background.uri.toString())
    }

    override fun describeContents(): Int {
        return 0
    }

    companion object CREATOR : Parcelable.Creator<BackgroundParcelable> {
        override fun createFromParcel(parcel: Parcel): BackgroundParcelable {
            return BackgroundParcelable(parcel)
        }

        override fun newArray(size: Int): Array<BackgroundParcelable?> {
            return arrayOfNulls(size)
        }
    }
}