package me.magnum.melonds.domain.model.retroachievements

import me.magnum.rcheevosapi.model.RAAchievement

data class RAUserAchievement(
    val achievement: RAAchievement,
    val isUnlocked: Boolean,
    val forHardcoreMode: Boolean,
) {

    /**
     * Returns the number of points that this achievement is worth for the user in the current state. This depends on the actual number of points that the achievements awards,
     * whether the user has unlocked it or not and if it was unlocked in hardcore or softcore mode.
     */
    fun userPointsWorth(): Int {
        return if (isUnlocked) {
            achievement.points
        } else {
            0
        }
    }
}