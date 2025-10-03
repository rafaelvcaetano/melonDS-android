package me.magnum.melonds.domain.model

data class Cheat(
    val id: Long?,
    val cheatDatabaseId: Long,
    val name: String,
    val description: String?,
    val code: String,
    val enabled: Boolean,
)