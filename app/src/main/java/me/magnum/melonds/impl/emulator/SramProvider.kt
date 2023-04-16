package me.magnum.melonds.impl.emulator

import android.net.Uri
import me.magnum.melonds.common.uridelegates.UriHandler
import me.magnum.melonds.domain.model.Rom
import me.magnum.melonds.domain.repositories.SettingsRepository

class SramProvider(
    private val settingsRepository: SettingsRepository,
    private val uriHandler: UriHandler,
) {

    @Throws(SramLoadException::class)
    fun getSramForRom(rom: Rom): Uri {
        val rootDirUri = settingsRepository.getSaveFileDirectory(rom)

        val rootDocument = uriHandler.getUriTreeDocument(rootDirUri) ?: throw SramLoadException("Cannot create root document: $rootDirUri")
        val romDocument = uriHandler.getUriDocument(rom.uri)

        val romFileName = romDocument?.name ?: throw SramLoadException("Cannot determine SRAM file name: ${romDocument?.uri}")
        val sramFileName = romFileName.replaceAfterLast('.', "sav", "$romFileName.sav")

        val sramDocument = rootDocument.findFile(sramFileName)
        return sramDocument?.uri ?: rootDocument.createFile("*/*", sramFileName)?.uri ?: throw SramLoadException("Could not create temporary SRAM file at ${rootDocument.uri}")
    }
}