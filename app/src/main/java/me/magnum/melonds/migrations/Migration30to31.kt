package me.magnum.melonds.migrations

import me.magnum.melonds.migrations.helper.GenericJsonArrayMigrationHelper
import me.magnum.melonds.migrations.legacy.RomConfigDto25
import me.magnum.melonds.migrations.legacy.RomConfigDto31
import me.magnum.melonds.migrations.legacy.RomDto25
import me.magnum.melonds.migrations.legacy.RomDto31
import me.magnum.melonds.migrations.legacy.RomGbaSlotConfigDto31

class Migration30to31(
    private val romMigrationHelper: GenericJsonArrayMigrationHelper,
) : Migration {

    override val from = 30
    override val to = 31

    override fun migrate() {
        romMigrationHelper.migrateJsonArrayData(ROM_DATA_FILE, RomDto25::class.java) {
            RomDto31(
                it.name,
                it.developerName,
                it.fileName,
                it.uri,
                it.parentTreeUri,
                RomConfigDto31(
                    it.config.runtimeConsoleType,
                    it.config.runtimeMicSource,
                    it.config.layoutId,
                    createGbaSlotConfigDto(it.config),
                ),
                it.lastPlayed,
                it.isDsiWareTitle,
                it.retroAchievementsHash,
            )
        }
    }

    private fun createGbaSlotConfigDto(oldConfig: RomConfigDto25): RomGbaSlotConfigDto31 {
        return RomGbaSlotConfigDto31(
            type = if (oldConfig.loadGbaCart) RomGbaSlotConfigDto31.Type.GbaRom else RomGbaSlotConfigDto31.Type.None,
            gbaRomPath = oldConfig.gbaCartPath,
            gbaSavePath = oldConfig.gbaSavePath,
        )
    }

    companion object {
        private const val ROM_DATA_FILE = "rom_data.json"
    }
}