package me.magnum.melonds.domain.repositories

import android.net.Uri
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import me.magnum.melonds.domain.model.Rom
import me.magnum.melonds.domain.model.RomConfig
import me.magnum.melonds.domain.model.RomScanningStatus
import java.util.*

interface RomsRepository {
    fun getRoms(): Flow<List<Rom>>
    fun getRomScanningStatus(): StateFlow<RomScanningStatus>
    suspend fun getRomAtPath(path: String): Rom?
    suspend fun getRomAtUri(uri: Uri): Rom?

    fun updateRomConfig(rom: Rom, romConfig: RomConfig)
    fun setRomLastPlayed(rom: Rom, lastPlayed: Date)
    fun rescanRoms()
    fun invalidateRoms()
}
