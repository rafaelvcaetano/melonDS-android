package me.magnum.melonds.domain.model.retroachievements

import java.net.URL

data class RAGameSummary(
    val title: String,
    val icon: URL,
    val richPresencePatch: String?,
)