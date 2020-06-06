package me.magnum.melonds.repositories

import io.reactivex.Observable
import me.magnum.melonds.model.Rom
import me.magnum.melonds.model.RomConfig
import me.magnum.melonds.model.RomScanningStatus
import java.util.*

interface RomsRepository {
    fun getRoms(): Observable<List<Rom>>
    fun getRomScanningStatus(): Observable<RomScanningStatus>

    fun updateRomConfig(rom: Rom, romConfig: RomConfig)
    fun setRomLastPlayed(rom: Rom, lastPlayed: Date)
    fun rescanRoms()
}
