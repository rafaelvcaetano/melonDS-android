package me.magnum.melonds.migrations

import me.magnum.melonds.domain.repositories.RomsRepository

class Migration16to17(private val romsRepository: RomsRepository) : Migration {
    override val from = 16
    override val to = 17

    override fun migrate() {
        romsRepository.invalidateRoms()
    }
}