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
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

abstract class CompressedRomFileProcessor(private val context: Context, private val uriHandler: UriHandler, private val ndsRomCache: NdsRomCache) : RomFileProcessor {

    private sealed class RomExtractionException(message: String) : Exception(message)
    private class CouldNotFindNdsRomException : RomExtractionException("Failed to open the compressed file or to find an NDS ROM in it")
    private class CouldNotFindExtractedFileException : RomExtractionException("Failed to find extracted NDS ROM file")

    private companion object {
        val SUPPORTED_ROM_EXTENSIONS = listOf("nds", "dsi", "ids")

        /**
         * Held while an archive is open. Compressed ROMs allocate large decoder buffers on the Java heap (the LZMA2
         * dictionary of a 7z archive alone can take a good part of it), so at most one archive is decoded at a time
         * in the whole process, whichever component needs it (ROM scan, icon extraction, ROM info, extraction to the
         * cache). Reads of already extracted ROMs never take it, and plain ROM files are not affected at all.
         */
        val archiveLock = ReentrantLock()
    }

    override fun getRomFromUri(romUri: Uri, parentUri: Uri?): Rom? {
        return try {
            readArchivedRom(romUri) { romFileStream ->
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
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    override fun getRomIcon(rom: Rom): Bitmap? {
        return try {
            readRom(rom) {
                RomProcessor.getRomIcon(it)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    override fun getRomInfo(rom: Rom): RomInfo? {
        return try {
            readRom(rom) {
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

    /**
     * Runs [block] on the best available stream for [rom]: the extracted copy when the cache has one, otherwise the
     * ROM entry decoded from the archive. Returns null if neither could be opened.
     */
    private inline fun <T> readRom(rom: Rom, block: (InputStream) -> T): T? {
        val cachedRomUri = ndsRomCache.getCachedRomFile(rom)
        return if (cachedRomUri != null) {
            context.contentResolver.openInputStream(cachedRomUri)?.use(block)
        } else {
            readArchivedRom(rom.uri, block)
        }
    }

    /**
     * Opens the archive at [romUri], decodes its ROM entry and runs [block] on it while holding [archiveLock], so
     * that the decoder buffers of only one archive exist at a time. Returns null if the archive could not be opened
     * or contains no ROM.
     */
    private inline fun <T> readArchivedRom(romUri: Uri, block: (RomFileStream) -> T): T? {
        return archiveLock.withLock {
            context.contentResolver.openInputStream(romUri)?.use { fileStream ->
                getNdsEntryStreamInFileStream(fileStream)?.use(block)
            }
        }
    }

    private fun getRomMetadataInZipEntry(inputStream: InputStream): RomMetadata? {
        return RomProcessor.getRomMetadata(inputStream)
    }

    private suspend fun extractRomFile(rom: Rom): Uri? = suspendCoroutine { continuation ->
        // The whole extraction runs synchronously inside this block, so the archive lock taken by readArchivedRom is
        // never held across a suspension point
        readArchivedRom(rom.uri) { romFileStream ->
            ndsRomCache.cacheRom(rom, object : NdsRomCache.RomExtractor {
                override fun getExtractedRomFileSize(): SizeUnit? {
                    return romFileStream.romFileSize
                }

                override fun saveRomFile(fileStream: OutputStream): Boolean {
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
    }

    /**
     * Retrieves the [RomFileStream] that points to the ROM in the compressed file. May return null if a ROM entry was not found in the compressed archive.
     */
    abstract fun getNdsEntryStreamInFileStream(fileStream: InputStream): RomFileStream?

    class RomFileStream(stream: InputStream, val romFileSize: SizeUnit?) : FilterInputStream(
        BoundedArchiveInputStream(stream, ArchiveReadBudget.MAX_ROM_BYTES),
    )
}
