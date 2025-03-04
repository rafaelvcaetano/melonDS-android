package me.magnum.melonds.domain.repositories

import android.graphics.Bitmap
import android.net.Uri
import me.magnum.melonds.domain.model.rom.Rom
import me.magnum.melonds.domain.model.SaveStateSlot

interface SaveStatesRepository {
    fun getRomSaveStates(rom: Rom): List<SaveStateSlot>
    fun getRomQuickSaveStateSlot(rom: Rom): SaveStateSlot
    fun getRomSaveStateUri(rom: Rom, saveState: SaveStateSlot): Uri
    fun setRomSaveStateScreenshot(rom: Rom, saveState: SaveStateSlot, screenshot: Bitmap)
    fun deleteRomSaveState(rom: Rom, saveState: SaveStateSlot)
}