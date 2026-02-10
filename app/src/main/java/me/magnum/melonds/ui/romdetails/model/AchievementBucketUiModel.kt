package me.magnum.melonds.ui.romdetails.model

import me.magnum.melonds.ui.common.achievements.ui.model.AchievementUiModel

data class AchievementBucketUiModel(
    val bucket: Bucket,
    val achievements: List<AchievementUiModel>,
) {

    /**
     * The different buckets into which displayed achievements can be inserted. The enum entries are defined in their preferred display order.
     */
    enum class Bucket(val displayOrder: Int) {
        ActiveChallenges(0),
        RecentlyUnlocked(1),
        AlmostThere(2),
        Locked(3),
        Unlocked(4),
    }
}