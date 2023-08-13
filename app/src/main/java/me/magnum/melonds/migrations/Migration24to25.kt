package me.magnum.melonds.migrations

import android.content.Context
import me.magnum.melonds.migrations.helper.GenericJsonArrayMigrationHelper
import me.magnum.melonds.migrations.legacy.Rom22
import me.magnum.melonds.migrations.legacy.RomConfigDto25
import me.magnum.melonds.migrations.legacy.RomDto25
import me.magnum.melonds.utils.RomProcessor

class Migration24to25(
    private val romMigrationHelper: GenericJsonArrayMigrationHelper,
    private val context: Context,
) : Migration {

    companion object {
        private const val ROM_DATA_FILE = "rom_data.json"
    }

    override val from = 24
    override val to = 25

    override fun migrate() {
        romMigrationHelper.migrateJsonArrayData<Rom22, RomDto25>(ROM_DATA_FILE) { rom ->
            runCatching {
                context.contentResolver.openInputStream(rom.uri)?.use {
                    RomProcessor.getRomMetadata(it.buffered())
                }
            }.getOrNull()?.let {
                RomDto25(
                    rom.name,
                    it.developerName,
                    rom.fileName,
                    rom.uri.toString(),
                    rom.parentTreeUri.toString(),
                    RomConfigDto25(
                        rom.config.runtimeConsoleType,
                        rom.config.runtimeMicSource,
                        rom.config.layoutId?.toString(),
                        rom.config.loadGbaCart,
                        rom.config.gbaCartPath?.toString(),
                        rom.config.gbaSavePath?.toString(),
                    ),
                    rom.lastPlayed,
                    it.isDSiWareTitle,
                    it.retroAchievementsHash,
                )
            }
        }
    }
}