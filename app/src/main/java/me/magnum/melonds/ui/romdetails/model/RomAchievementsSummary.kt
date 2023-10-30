package me.magnum.melonds.ui.romdetails.model

data class RomAchievementsSummary(
    val forHardcoreMode: Boolean,
    val totalAchievements: Int,
    val completedAchievements: Int,
    val totalPoints: Int,
) {

    val completedPercentage get() = (completedAchievements / totalAchievements.toFloat() * 100).toInt()
}