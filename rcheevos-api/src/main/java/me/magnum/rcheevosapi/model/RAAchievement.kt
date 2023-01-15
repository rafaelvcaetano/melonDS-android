package me.magnum.rcheevosapi.model

import java.net.URL

data class RAAchievement(
    val id: Long,
    val totalAwardsCasual: Int,
    val totalAwardsHardcore: Int,
    val title: String,
    val description: String,
    val points: Int,
    val displayOrder: Int,
    val badgeUrlUnlocked: URL,
    val badgeUrlLocked: URL,
    val memoryAddress: String,
)