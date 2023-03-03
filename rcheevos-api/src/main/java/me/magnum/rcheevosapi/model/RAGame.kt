package me.magnum.rcheevosapi.model

data class RAGame(
    val id: RAGameId,
    val title: String,
    val richPresencePatch: String?,
    val achievements: List<RAAchievement>,
)