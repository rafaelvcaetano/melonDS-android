package me.magnum.melonds.common

interface RetroAchievementsCallback {
    fun onAchievementPrimed(achievementId: Long)
    fun onAchievementTriggered(achievementId: Long)
    fun onAchievementUnprimed(achievementId: Long)
}