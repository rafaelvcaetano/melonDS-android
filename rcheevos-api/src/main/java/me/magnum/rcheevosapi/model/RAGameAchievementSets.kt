package me.magnum.rcheevosapi.model

import java.net.URL

data class RAGameAchievementSets(
    val id: RAGameId,
    val title: String,
    val icon: URL,
    val richPresencePatch: String?,
    val richPresenceGameId: RAGameId?,
    val sets: List<RAAchievementSet>?,
)