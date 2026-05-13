package me.magnum.melonds.common.retroachievements

import androidx.preference.PreferenceDataStore

class RetroAchievementsPreferenceDataStore(
    private val retroAchievementsIniStore: RetroAchievementsIniStore,
) : PreferenceDataStore() {

    override fun putBoolean(key: String?, value: Boolean) {
        when (key) {
            "ra_hardcore_enabled" -> retroAchievementsIniStore.setHardcoreEnabled(value)
            "ra_rich_presence" -> retroAchievementsIniStore.setRichPresenceEnabled(value)
            "ra_active_challenge_indicators" -> retroAchievementsIniStore.setActiveChallengeIndicatorsEnabled(value)
            "ra_progress_indicators" -> retroAchievementsIniStore.setProgressIndicatorsEnabled(value)
            "ra_leaderboard_indicators" -> retroAchievementsIniStore.setLeaderboardIndicatorsEnabled(value)
        }
    }

    override fun getBoolean(key: String?, defValue: Boolean): Boolean {
        return when (key) {
            "ra_hardcore_enabled" -> retroAchievementsIniStore.isHardcoreEnabled()
            "ra_rich_presence" -> retroAchievementsIniStore.isRichPresenceEnabled()
            "ra_active_challenge_indicators" -> retroAchievementsIniStore.areActiveChallengeIndicatorsEnabled()
            "ra_progress_indicators" -> retroAchievementsIniStore.areProgressIndicatorsEnabled()
            "ra_leaderboard_indicators" -> retroAchievementsIniStore.areLeaderboardIndicatorsEnabled()
            else -> defValue
        }
    }
}
