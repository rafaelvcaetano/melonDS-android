package me.magnum.melonds.impl.emulator

import me.magnum.melonds.domain.model.ConsoleType
import me.magnum.melonds.domain.model.Rom
import me.magnum.melonds.domain.model.emulator.EmulatorSessionUpdateAction
import me.magnum.melonds.domain.model.retroachievements.GameAchievementData

class EmulatorSession {

    var isRetroAchievementsHardcoreModeEnabled = false
        private set

    private var areRetroAchievementsEnabled = false
    private var isRetroAchievementsIntegrationEnabled = false
    private var sessionType: SessionType? = null

    fun startSession(areRetroAchievementsEnabled: Boolean, isRetroAchievementsHardcoreModeEnabled: Boolean, sessionType: SessionType) {
        this.areRetroAchievementsEnabled = areRetroAchievementsEnabled
        // Hardcore mode can only be enabled if RetroAchievements are available when the session starts
        this.isRetroAchievementsHardcoreModeEnabled = areRetroAchievementsEnabled && isRetroAchievementsHardcoreModeEnabled
        this.sessionType = sessionType
    }

    fun reset() {
        areRetroAchievementsEnabled = false
        isRetroAchievementsHardcoreModeEnabled = false
        isRetroAchievementsIntegrationEnabled = false
        sessionType = null
    }

    fun updateRetroAchievementsSettings(areRetroAchievementsEnabled: Boolean, isHardcoreModeEnabled: Boolean): List<EmulatorSessionUpdateAction> {
        val updateActions = mutableListOf<EmulatorSessionUpdateAction>()

        if (this.areRetroAchievementsEnabled != areRetroAchievementsEnabled) {
            if (areRetroAchievementsEnabled) {
                updateActions.add(EmulatorSessionUpdateAction.EnableRetroAchievements)
            } else {
                updateActions.add(EmulatorSessionUpdateAction.DisableRetroAchievements)
            }
        }

        if (!isHardcoreModeEnabled) {
            // Cannot enable hardcore mode at runtime
            isRetroAchievementsHardcoreModeEnabled = false
        }

        this.areRetroAchievementsEnabled = areRetroAchievementsEnabled

        return updateActions
    }

    fun updateRetroAchievementsIntegrationStatus(integrationStatus: GameAchievementData.IntegrationStatus) {
        isRetroAchievementsIntegrationEnabled = integrationStatus == GameAchievementData.IntegrationStatus.ENABLED
    }

    fun areRetroAchievementsEnabled(): Boolean {
        return areRetroAchievementsEnabled && isRetroAchievementsIntegrationEnabled
    }

    fun areSaveStatesAllowed(): Boolean {
        // Cannot use save-states when RA hardcore is enabled
        return !isRetroAchievementsHardcoreModeEnabled || !areRetroAchievementsEnabled
    }

    fun areCheatsEnabled(): Boolean {
        // Cannot use cheats when RA hardcore is enabled
        return !isRetroAchievementsHardcoreModeEnabled || !areRetroAchievementsEnabled
    }

    fun currentSessionType(): SessionType? {
        return sessionType
    }

    sealed class SessionType {
        data class RomSession(val rom: Rom) : SessionType()
        data class FirmwareSession(val consoleType: ConsoleType) : SessionType()
    }
}