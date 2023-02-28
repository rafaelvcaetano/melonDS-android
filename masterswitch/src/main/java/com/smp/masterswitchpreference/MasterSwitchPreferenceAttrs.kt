package com.smp.masterswitchpreference

import android.graphics.Color
import android.os.Parcel
import android.os.Parcelable
import androidx.annotation.Keep

internal const val ATTRS_KEY_NAME = "MasterSwitchAttrs"

@Keep
class MasterSwitchPreferenceAttrs(
    val switchThumbColor: Int = Color.WHITE,
    val switchTrackColor: Int = (Color.WHITE - 0x90000000).toInt(),
    val switchOnBackgroundColor: Int = Color.RED,
    val switchOffBackgroundColor: Int = Color.LTGRAY,
    val switchTextColor: Int = Color.BLACK,
    val switchOffExplanationText: String = "",
    val switchOnExplanationText: String = "",
    val includedPrefScreen: Int? = null,
    val excludedPrefScreen: Int? = null,
    val switchOnText: String = "On",
    val switchOffText: String = "Off",
    val hideExplanation: Boolean = true,
    val key: String = "master_switch_key",
    val defaultValue: Boolean = false,
    val explanationIcon: Int? = null,
    val showStatus: Boolean = false
) : Parcelable {

    constructor(parcel: Parcel) : this(
        parcel.readInt(),
        parcel.readInt(),
        parcel.readInt(),
        parcel.readInt(),
        parcel.readInt(),
        parcel.readString() ?: "",
        parcel.readString() ?: "",
        parcel.readValue(Int::class.java.classLoader) as? Int,
        parcel.readValue(Int::class.java.classLoader) as? Int,
        parcel.readString() ?: "On",
        parcel.readString() ?: "Off",
        parcel.readByte() != 0.toByte(),
        parcel.readString() ?: "master_switch_key",
        parcel.readByte() != 0.toByte(),
        parcel.readValue(Int::class.java.classLoader) as? Int,
        parcel.readByte() != 0.toByte()
    )

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeInt(switchThumbColor)
        parcel.writeInt(switchTrackColor)
        parcel.writeInt(switchOnBackgroundColor)
        parcel.writeInt(switchOffBackgroundColor)
        parcel.writeInt(switchTextColor)
        parcel.writeString(switchOffExplanationText)
        parcel.writeString(switchOnExplanationText)
        parcel.writeValue(includedPrefScreen)
        parcel.writeValue(excludedPrefScreen)
        parcel.writeString(switchOnText)
        parcel.writeString(switchOffText)
        parcel.writeByte(if (hideExplanation) 1 else 0)
        parcel.writeString(key)
        parcel.writeByte(if (defaultValue) 1 else 0)
        parcel.writeValue(explanationIcon)
        parcel.writeByte(if (showStatus) 1 else 0)
    }

    override fun describeContents(): Int {
        return 0
    }

    companion object CREATOR : Parcelable.Creator<MasterSwitchPreferenceAttrs> {
        override fun createFromParcel(parcel: Parcel): MasterSwitchPreferenceAttrs {
            return MasterSwitchPreferenceAttrs(parcel)
        }

        override fun newArray(size: Int): Array<MasterSwitchPreferenceAttrs?> {
            return arrayOfNulls(size)
        }
    }
}
