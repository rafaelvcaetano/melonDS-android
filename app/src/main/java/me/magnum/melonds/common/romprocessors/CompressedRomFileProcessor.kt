package me.magnum.melonds.common.romprocessors

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import kotlinx.coroutines.isActive
import me.magnum.melonds.common.uridelegates.UriHandler
import me.magnum.melonds.domain.model.RomInfo
import me.magnum.melonds.domain.model.RomMetadata
import me.magnum.melonds.domain.model.SizeUnit
import me.magnum.melonds.domain.model.rom.Rom
import me.magnum.melonds.domain.model.rom.config.RomConfig
import me.magnum.melonds.extensions.isBlank
import me.magnum.melonds.extensions.nameWithoutExtension
import me.magnum.melonds.impl.NdsRomCache
import me.magnum.melonds.utils.RomProcessor
import java.io.FileOutputStream
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

abstract class CompressedRomFileProcessor(private val context: Context, private val uriHandler: UriHandler, private val ndsRomCache: NdsRomCache) : RomFileProcessor {

    private sealed class RomExtractionException(message: String) : Exception(message)
    private class CouldNotOpenCompressedFileException : RomExtractionException("Failed to open compressed file for extraction")
    private class CouldNotFindNdsRomException : RomExtractionException("Failed to find an NDS ROM to extract")
    private class CouldNotFindExtractedFileException : RomExtractionException("Failed to find extracted NDS ROM file")

    private companion object {
        val SUPPORTED_ROM_EXTENSIONS = listOf("nds", "dsi", "ids")
    }

    override fun getRomFromUri(romUri: Uri, parentUri: Uri?): Rom? {
        return try {
            context.contentResolver.openInputStream(romUri)?.use { stream ->
                getNdsEntryStreamInFileStream(stream, romUri)?.use { romFileStream ->
                    val romDocument = uriHandler.getUriDocument(romUri)
                    getRomMetadataInZipEntry(romFileStream)?.let { romMetadata ->
                        val romName = romMetadata.romTitle.takeUnless { it.isBlank() } ?: romDocument?.nameWithoutExtension ?: ""
                        Rom(
                            name = romName,
                            developerName = romMetadata.developerName,
                            fileName = romDocument?.name ?: "",
                            uri = romUri,
                            parentTreeUri = parentUri,
                            config = if (romMetadata.isDSiWareTitle) RomConfig.forDsiWareTitle() else RomConfig.default(),
                            lastPlayed = null,
                            isDsiWareTitle = romMetadata.isDSiWareTitle,
                            retroAchievementsHash = romMetadata.retroAchievementsHash
                        )
                    }
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
                RomProcessor.getRomIcon(it)
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

    override suspend fun getRealRomUri(rom: Rom): Uri? {
        val cachedRomUri = ndsRomCache.getCachedRomFile(rom, true)
        return if (cachedRomUri != null) {
            cachedRomUri
        } else {
            try {
                extractRomFile(rom)
            } catch (_: RomExtractionException) {
                null
            }
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
                getNdsEntryStreamInFileStream(it, rom.uri)
            }
        }
    }

    private fun getRomMetadataInZipEntry(inputStream: InputStream): RomMetadata? {
        return RomProcessor.getRomMetadata(inputStream)
    }

    private suspend fun extractRomFile(rom: Rom): Uri? = suspendCoroutine { continuation ->
        context.contentResolver.openInputStream(rom.uri)?.use {
            getNdsEntryStreamInFileStream(it, rom.uri)?.use { romFileStream ->
                ndsRomCache.cacheRom(rom, object : NdsRomCache.RomExtractor {
                    override fun getExtractedRomFileSize(): SizeUnit {
                        return romFileStream.romFileSize
                    }

                    override fun saveRomFile(fileStream: FileOutputStream): Boolean {
                        val buffer = ByteArray(8192)

                        try {
                            do {
                                val read = romFileStream.read(buffer)
                                if (read <= 0) {
                                    break
                                }

                                fileStream.write(buffer, 0, read)
                            } while (continuation.context.isActive)
                        } catch (_: IOException) {
                            return false
                        }

                        return continuation.context.isActive
                    }
                })

                if (continuation.context.isActive) {
                    val cachedRomUri = ndsRomCache.getCachedRomFile(rom)
                    if (cachedRomUri == null) {
                        continuation.resumeWithException(CouldNotFindExtractedFileException())
                    } else {
                        continuation.resume(cachedRomUri)
                    }
                }
            } ?: continuation.resumeWithException(CouldNotFindNdsRomException())
        } ?: continuation.resumeWithException(CouldNotOpenCompressedFileException())
    }

    /**
     * Retrieves the [RomFileStream] that points to the ROM in the compressed file. May return null if a ROM entry was not found in the compressed archive. The
     * [romUri] of the compressed file is provided so that implementations can report errors that reference the file.
     */
    abstract fun getNdsEntryStreamInFileStream(fileStream: InputStream, romUri: Uri): RomFileStream?

    class RomFileStream(stream: InputStream, val romFileSize: SizeUnit) : FilterInputStream(stream)
}