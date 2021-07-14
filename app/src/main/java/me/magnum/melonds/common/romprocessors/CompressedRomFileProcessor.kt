package me.magnum.melonds.common.romprocessors

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import io.reactivex.Single
import me.magnum.melonds.domain.model.Rom
import me.magnum.melonds.domain.model.RomConfig
import me.magnum.melonds.domain.model.RomInfo
import me.magnum.melonds.impl.NdsRomCache
import me.magnum.melonds.utils.RomProcessor
import java.io.InputStream

abstract class CompressedRomFileProcessor(private val context: Context, private val ndsRomCache: NdsRomCache) : RomFileProcessor {
    private class CouldNotOpenCompressedFileException : Exception("Failed to open compressed file for extraction")
    private class CouldNotFindNdsRomException : Exception("Failed to find an NDS ROM to extract")
    private class CouldNotFindExtractedFileException : Exception("Failed to find extracted NDS ROM file")

    override fun getRomFromUri(romUri: Uri, parentUri: Uri): Rom? {
        return try {
            context.contentResolver.openInputStream(romUri)?.use {
                getNdsEntryStreamInFileStream(it)?.use { romFileStream ->
                    val romName = getRomNameInZipEntry(romFileStream)
                    Rom(romName, romUri, parentUri, RomConfig())
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
                RomProcessor.getRomInfo(it)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    override fun getRealRomUri(rom: Rom): Single<Uri> {
        val cachedRomUri = ndsRomCache.getCachedRomFile(rom)
        return if (cachedRomUri != null) {
            Single.just(cachedRomUri)
        } else {
            extractRomFile(rom)
        }
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

    private fun getRomNameInZipEntry(inputStream: InputStream): String {
        return RomProcessor.getRomName(inputStream.buffered())
    }

    private fun extractRomFile(rom: Rom): Single<Uri> {
        return Single.create { emitter ->
            context.contentResolver.openInputStream(rom.uri)?.use {
                getNdsEntryStreamInFileStream(it)?.use { romFileStream ->
                    ndsRomCache.cacheRom(rom) { fileOutputStream ->
                        val buffer = ByteArray(8192)

                        do {
                            val read = romFileStream.read(buffer)
                            if (read <= 0) {
                                break
                            }

                            fileOutputStream.write(buffer, 0, read)
                        } while (!emitter.isDisposed)

                        !emitter.isDisposed
                    }

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
     * Retrieves the [InputStream] that points to the ROM in the compressed file. May return null if a ROM entry was not found in the compressed archive.
     */
    abstract fun getNdsEntryStreamInFileStream(fileStream: InputStream): InputStream?
}