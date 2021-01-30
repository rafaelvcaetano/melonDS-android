package me.magnum.melonds.migrations

import me.magnum.melonds.utils.RomIconProvider

class Migration7to8(private val romIconProvider: RomIconProvider) : Migration {
    override val from: Int
        get() = 7
    override val to: Int
        get() = 8

    override fun migrate() {
        // Delete cached icons since the generation logic has changed
        romIconProvider.clearCachedIcons()
    }
}