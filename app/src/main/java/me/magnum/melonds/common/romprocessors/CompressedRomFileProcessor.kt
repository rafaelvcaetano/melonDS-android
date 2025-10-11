package me.magnum.melonds.common.romprocessors

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import io.reactivex.Single
import me.magnum.melonds.common.uridelegates.UriHandler
import me.magnum.melonds.domain.model.*
import me.magnum.melonds.domain.model.rom.Rom
import me.magnum.melonds.domain.model.rom.config.RomConfig
import me.magnum.melonds.extensions.isBlank
import me.magnum.melonds.extensions.nameWithoutExtension
import me.magnum.melonds.impl.NdsRomCache
import me.magnum.melonds.utils.RomProcessor
import java.io.FileOutputStream
import java.io.FilterInputStream
import java.io.InputStream

abstract class CompressedRomFileProcessor(private val context: Context, private val uriHandler: UriHandler, private val ndsRomCache: NdsRomCache) : RomFileProcessor {

    private class CouldNotOpenCompressedFileException : Exception("Failed to open compressed file for extraction")
    private class CouldNotFindNdsRomException : Exception("Failed to find an NDS ROM to extract")
    private class CouldNotFindExtractedFileException : Exception("Failed to find extracted NDS ROM file")

    private companion object {
        val SUPPORTED_ROM_EXTENSIONS = listOf("nds", "dsi", "ids")
    }

    override fun getRomFromUri(romUri: Uri, parentUri: Uri?): Rom? {
        return try {
            context.contentResolver.openInputStream(romUri)?.use { stream ->
                getNdsEntryStreamInFileStream(stream)?.use { romFileStream ->
                    val romDocument = uriHandler.getUriDocument(romUri)
                    val romMetadata = getRomMetadataInZipEntry(romFileStream)
                    val romName = romMetadata.romTitle.takeUnless { it.isBlank() } ?: romDocument?.nameWithoutExtension ?: ""
                    Rom(
                        name = romName,
                        developerName = romMetadata.developerName,
                        fileName = romDocument?.name ?: "",
                        uri = romUri,
                        parentTreeUri = parentUri,
                        config = RomConfig(),
                        lastPlayed = null,
                        isDsiWareTitle = romMetadata.isDSiWareTitle,
                        retroAchievementsHash = romMetadata.retroAchievementsHash
                    )
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    override fun getRomIcon(rom: Rom): Bitmap? {
        return try {
            getBestRomInputStream(rom)?.use {
                RomProcessor.getRomIcon(it.buffered())
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    override fun getRomInfo(rom: Rom): RomInfo? {
        return try {
            getBestRomInputStream(rom)?.use {
                RomProcessor.getRomInfo(rom, it)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    override fun getRealRomUri(rom: Rom): Single<Uri> {
        val cachedRomUri = ndsRomCache.getCachedRomFile(rom, true)
        return if (cachedRomUri != null) {
            Single.just(cachedRomUri)
        } else {
            extractRomFile(rom)
        }
    }

    protected fun isSupportedRomFile(fileName: String): Boolean {
        val extension = fileName.substringAfterLast('.').lowercase()
        return SUPPORTED_ROM_EXTENSIONS.contains(extension)
    }

    private fun getBestRomInputStream(rom: Rom): InputStream? {
        val cachedRomUri = ndsRomCache.getCachedRomFile(rom)
        return if (cachedRomUri != null) {
            context.contentResolver.openInputStream(cachedRomUri)
        } else {
            context.contentResolver.openInputStream(rom.uri)?.let {
                getNdsEntryStreamInFileStream(it)
            }
        }
    }

    private fun getRomMetadataInZipEntry(inputStream: InputStream): RomMetadata {
        return RomProcessor.getRomMetadata(inputStream.buffered())
    }

    private fun extractRomFile(rom: Rom): Single<Uri> {
        return Single.create { emitter ->
            context.contentResolver.openInputStream(rom.uri)?.use {
                getNdsEntryStreamInFileStream(it)?.use { romFileStream ->
                    ndsRomCache.cacheRom(rom, object : NdsRomCache.RomExtractor {
                        override fun getExtractedRomFileSize(): SizeUnit {
                            return romFileStream.romFileSize
                        }

                        override fun saveRomFile(fileStream: FileOutputStream): Boolean {
                            val buffer = ByteArray(8192)

                            do {
                                val read = romFileStream.read(buffer)
                                if (read <= 0) {
                                    break
                                }

                                fileStream.write(buffer, 0, read)
                            } while (!emitter.isDisposed)

                            return !emitter.isDisposed
                        }
                    })

                    if (!emitter.isDisposed) {
                        val cachedRomUri = ndsRomCache.getCachedRomFile(rom)
                        if (cachedRomUri == null) {
                            emitter.onError(CouldNotFindExtractedFileException())
                        } else {
                            emitter.onSuccess(cachedRomUri)
                        }
                    }
                } ?: emitter.onError(CouldNotFindNdsRomException())
            } ?: emitter.onError(CouldNotOpenCompressedFileException())
        }
    }

    /**
     * Retrieves the [RomFileStream] that points to the ROM in the compressed file. May return null if a ROM entry was not found in the compressed archive.
     */
    abstract fun getNdsEntryStreamInFileStream(fileStream: InputStream): RomFileStream?

    class RomFileStream(stream: InputStream, val romFileSize: SizeUnit) : FilterInputStream(stream)
}