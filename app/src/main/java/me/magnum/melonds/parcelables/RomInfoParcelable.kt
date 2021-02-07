package me.magnum.melonds.parcelables

import android.os.Parcelable
import kotlinx.android.parcel.Parcelize
import me.magnum.melonds.domain.model.RomInfo

@Parcelize
class RomInfoParcelable(val gameCode: String, val gameTitle: String) : Parcelable {
    companion object {
        fun fromRomInfo(romInfo: RomInfo): RomInfoParcelable {
            return RomInfoParcelable(romInfo.gameCode, romInfo.gameTitle)
        }
    }

    fun toRomInfo(): RomInfo {
        return RomInfo(gameCode, gameTitle)
    }
}