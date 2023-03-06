package me.magnum.melonds.domain.model

data class RomMetadata(
    val romTitle: String,
    val developerName: String,
    val isDSiWareTitle: Boolean,
    val retroAchievementsHash: String,
)