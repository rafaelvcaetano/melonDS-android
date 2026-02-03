package me.magnum.melonds.ui.romdetails.model

import me.magnum.melonds.domain.model.retroachievements.RAUserAchievement
import me.magnum.rcheevosapi.model.RAAchievementSet
import java.net.URL

data class AchievementSetUiModel(
    val setId: Long,
    val setTitle: String?,
    val setType: RAAchievementSet.Type,
    val setIcon: URL,
    val setSummary: RomAchievementsSummary,
    val achievements: List<RAUserAchievement>,
)