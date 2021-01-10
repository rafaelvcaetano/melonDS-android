package me.magnum.melonds.migrations

interface Migration {
    val from: Int
    val to: Int

    fun migrate()
}