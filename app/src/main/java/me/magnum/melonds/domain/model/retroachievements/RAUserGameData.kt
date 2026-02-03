package me.magnum.melonds.domain.model.retroachievements

import me.magnum.rcheevosapi.model.RAGameId
import java.net.URL

data class RAUserGameData(
    val id: RAGameId,
    val title: String,
    val icon: URL,
    val richPresencePatch: String?,
    val sets: List<RAUserAchievementSet>,
)