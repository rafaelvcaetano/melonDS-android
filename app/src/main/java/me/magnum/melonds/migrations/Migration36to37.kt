package me.magnum.melonds.migrations

import me.magnum.melonds.domain.model.rom.config.RuntimeConsoleType
import me.magnum.melonds.impl.dtos.rom.RomDto
import me.magnum.melonds.migrations.helper.GenericJsonArrayMigrationHelper

class Migration36to37(private val layoutMigrationHelper: GenericJsonArrayMigrationHelper) : Migration {

    private companion object {
        const val ROM_DATA_FILE = "rom_data.json"
    }

    override val from = 36
    override val to = 37

    override fun migrate() {
        layoutMigrationHelper.migrateJsonArrayData<RomDto, RomDto>(ROM_DATA_FILE) {
            if (it.isDsiWareTitle) {
                // Force DSiWare titles to use the DSi system
                val updatedConfig = it.config.copy(runtimeConsoleType = RuntimeConsoleType.DSi)
                it.copy(config = updatedConfig)
            } else {
                it
            }
        }
    }
}