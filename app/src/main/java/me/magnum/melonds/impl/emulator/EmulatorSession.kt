package me.magnum.melonds.impl.emulator

class EmulatorSession(
    private var areRetroAchievementsEnabled: Boolean,
    enabledRetroAchievementsHardcoreMode: Boolean,
) {

    var isRetroAchievementsHardcoreModeEnabled: Boolean = enabledRetroAchievementsHardcoreMode
        private set

    fun updateRetroAchievementsSettings(areRetroAchievementsEnabled: Boolean, isHardcoreModeEnabled: Boolean): Boolean {
        this.areRetroAchievementsEnabled = areRetroAchievementsEnabled

        if (isHardcoreModeEnabled) {
            // Cannot enable hardcore mode at runtime
            return false
        }

        isRetroAchievementsHardcoreModeEnabled = isHardcoreModeEnabled
        return true
    }

    fun areSaveStatesAllowed(): Boolean {
        // Cannot use save-states when RA hardcore is enabled
        return !isRetroAchievementsHardcoreModeEnabled || !areRetroAchievementsEnabled
    }

    fun areCheatsEnabled(): Boolean {
        // Cannot use cheats when RA hardcore is enabled
        return !isRetroAchievementsHardcoreModeEnabled || !areRetroAchievementsEnabled
    }
}