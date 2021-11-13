package me.magnum.melonds.migrations

import me.magnum.melonds.common.DirectoryAccessValidator
import me.magnum.melonds.common.Permission
import me.magnum.melonds.domain.repositories.RomsRepository
import me.magnum.melonds.domain.repositories.SettingsRepository

/**
 * Deals with cases where users would have selected a ROM search directory that was actually a compressed file, which would not allow write operations. If such a directory is
 * detected in this migration, that directory will be removed so that the user can search a new one.
 */
class Migration20to21(
    private val settingsRepository: SettingsRepository,
    private val romsRepository: RomsRepository,
    private val directoryAccessValidator: DirectoryAccessValidator
) : Migration {

    override val from = 20
    override val to = 21

    override fun migrate() {
        val romSearchDirectory = settingsRepository.getRomSearchDirectories().firstOrNull() ?: return
        if (directoryAccessValidator.getDirectoryAccessForPermission(romSearchDirectory, Permission.READ_WRITE) == DirectoryAccessValidator.DirectoryAccessResult.OK) {
            // ROM search directory access has been validated. No action needed
            return
        }

        romsRepository.invalidateRoms()
        settingsRepository.clearRomSearchDirectories()
    }
}