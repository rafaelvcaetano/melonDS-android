package me.magnum.melonds.parcelables

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import me.magnum.melonds.domain.model.RomInfo

@Parcelize
class RomInfoParcelable(
    private val gameCode: String,
    private val headerChecksum: Int,
    private val gameTitle: String,
    private val gameName: String,
) : Parcelable {
    companion object {
        fun fromRomInfo(romInfo: RomInfo): RomInfoParcelable {
            return RomInfoParcelable(romInfo.gameCode, romInfo.headerChecksum.toInt(), romInfo.gameTitle, romInfo.gameName)
        }
    }

    fun toRomInfo(): RomInfo {
        return RomInfo(gameCode, headerChecksum.toUInt(), gameTitle, gameName)
    }
}