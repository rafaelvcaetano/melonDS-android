package me.magnum.rcheevosapi.model

/**
 * @param achievementAwarded Whether the achievement was actually awarded or not. If `false`, this means that the user had already unlocked the achievement
 * @param remainingAchievements The number of remaining achievements to unlock in the game
 */
data class RAAwardAchievementResponse(
    val achievementAwarded: Boolean,
    val remainingAchievements: Int,
) {

    fun isSetMastered(): Boolean {
        return achievementAwarded && remainingAchievements == 0
    }
}