package me.magnum.melonds.migrations

import me.magnum.melonds.utils.RomIconProvider

class Migration14to15(private val romIconProvider: RomIconProvider) : Migration {
    override val from = 14
    override val to = 15

    override fun migrate() {
        // Delete cached icons since the icon generation method has changed
        romIconProvider.clearIconCache()
    }
}