package me.magnum.melonds.domain.model.retroachievements

import me.magnum.rcheevosapi.model.RAAchievement

data class RAUserAchievement(
    val achievement: RAAchievement,
    val isUnlocked: Boolean,
)