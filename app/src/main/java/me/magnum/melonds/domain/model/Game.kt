package me.magnum.melonds.domain.model

data class Game(val id: Long?, val name: String, val gameCode: String, val cheats: List<CheatFolder>)