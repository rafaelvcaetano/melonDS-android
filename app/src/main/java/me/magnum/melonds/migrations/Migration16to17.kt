package me.magnum.melonds.migrations

import me.magnum.melonds.impl.FileSystemRomsRepository

class Migration16to17(private val romsRepository: FileSystemRomsRepository) : Migration {
    override val from = 16
    override val to = 17

    override fun migrate() {
        romsRepository.deleteCachedRomData()
    }
}