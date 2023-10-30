package me.magnum.rcheevosapi.model

import java.net.URL

data class RAGame(
    val id: RAGameId,
    val title: String,
    val icon: URL,
    val richPresencePatch: String?,
    val achievements: List<RAAchievement>,
)