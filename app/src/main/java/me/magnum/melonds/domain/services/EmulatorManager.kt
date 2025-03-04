package me.magnum.melonds.domain.services

import android.net.Uri
import kotlinx.coroutines.flow.Flow
import me.magnum.melonds.domain.model.Cheat
import me.magnum.melonds.domain.model.ConsoleType
import me.magnum.melonds.domain.model.rom.Rom
import me.magnum.melonds.domain.model.emulator.FirmwareLaunchResult
import me.magnum.melonds.domain.model.emulator.RomLaunchResult
import me.magnum.melonds.domain.model.render.FrameRenderEvent
import me.magnum.melonds.domain.model.retroachievements.GameAchievementData
import me.magnum.melonds.domain.model.retroachievements.RAEvent
import me.magnum.melonds.ui.emulator.rewind.model.RewindSaveState
import me.magnum.melonds.ui.emulator.rewind.model.RewindWindow

interface EmulatorManager {
    val frameRenderedEvent: Flow<FrameRenderEvent>

    suspend fun loadRom(rom: Rom, cheats: List<Cheat>, glContext: Long): RomLaunchResult

    suspend fun loadFirmware(consoleType: ConsoleType, glContext: Long): FirmwareLaunchResult

    suspend fun updateRomEmulatorConfiguration(rom: Rom)

    suspend fun updateFirmwareEmulatorConfiguration(consoleType: ConsoleType)

    suspend fun getRewindWindow(): RewindWindow

    fun getFps(): Int

    suspend fun pauseEmulator()

    suspend fun resumeEmulator()

    suspend fun resetEmulator(): Boolean

    suspend fun updateCheats(cheats: List<Cheat>)
    suspend fun setupAchievements(achievementData: GameAchievementData)
    fun unloadAchievements()

    suspend fun loadRewindState(rewindSaveState: RewindSaveState): Boolean

    suspend fun saveState(saveStateFileUri: Uri): Boolean

    suspend fun loadState(saveStateFileUri: Uri): Boolean

    fun stopEmulator()

    fun cleanEmulator()

    fun observeRetroAchievementEvents(): Flow<RAEvent>
}