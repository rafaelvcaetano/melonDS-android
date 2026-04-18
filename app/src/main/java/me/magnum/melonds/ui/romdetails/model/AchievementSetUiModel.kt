package me.magnum.melonds.ui.romdetails.model

import me.magnum.rcheevosapi.model.RAAchievementSet
import java.net.URL

data class AchievementSetUiModel(
    val setId: Long,
    val setTitle: String?,
    val setType: RAAchievementSet.Type,
    val setIcon: URL,
    val setSummary: RomAchievementsSummary,
    val buckets: List<AchievementBucketUiModel>,
)