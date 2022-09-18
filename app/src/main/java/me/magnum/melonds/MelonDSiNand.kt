package me.magnum.melonds

import me.magnum.melonds.domain.model.DSiWareTitle
import me.magnum.melonds.domain.model.EmulatorConfiguration

object MelonDSiNand {
    external fun openNand(emulatorConfiguration: EmulatorConfiguration): Int
    external fun listTitles(): ArrayList<DSiWareTitle>
    external fun importTitle(titleUri: String, tmdMetadata: ByteArray): Int
    external fun deleteTitle(titleId: Int)
    external fun closeNand()
}