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
import java.io.BufferedInputStream
import java.io.InputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

class ZipRomFileProcessor(private val context: Context, private val ndsRomCache: NdsRomCache) : RomFileProcessor {
    private class CouldNotOpenZipFileException : Exception("Failed to open ZIP file for extraction")
    private class CouldNotFindNdsRomException : Exception("Failed to find an NDS ROM to extract")
    private class CouldNotFindExtractedFileException : Exception("Failed to find extracted NDS ROM file")

    override fun getRomFromUri(romUri: Uri, parentUri: Uri): Rom? {
        return try {
            context.contentResolver.openInputStream(romUri)?.use {
                val zipStream = ZipInputStream(it)
                getNdsEntryInStream(zipStream)?.let {
                    val romName = getRomNameInZipEntry(zipStream)
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

    /**
     * Returns the best input stream that can be used when processing the given ROM. If the ROM has been previously extracted and is cached, use it's input stream. Otherwise,
     * use the zip's input stream.
     */
    private fun getBestRomInputStream(rom: Rom): InputStream? {
        val cachedRomUri = ndsRomCache.getCachedRomFile(rom)
        return if (cachedRomUri != null) {
            context.contentResolver.openInputStream(cachedRomUri)
        } else {
            context.contentResolver.openInputStream(rom.uri)?.let {
                val zipStream = ZipInputStream(it)
                try {
                    if (getNdsEntryInStream(zipStream) != null) {
                        BufferedInputStream(zipStream)
                    } else {
                        zipStream.close()
                        null
                    }
                } catch (e: Exception) {
                    zipStream.close()
                    null
                }
            }
        }
    }

    private fun getNdsEntryInStream(inputStream: ZipInputStream): ZipEntry? {
        do {
            val nextEntry = inputStream.nextEntry ?: break
            if (nextEntry.name.endsWith(".nds")) {
                return nextEntry
            }
        } while (true)
        return null
    }

    private fun getRomNameInZipEntry(inputStream: ZipInputStream): String {
        return RomProcessor.getRomName(inputStream.buffered())
    }

    private fun extractRomFile(rom: Rom): Single<Uri> {
        return Single.create { emitter ->
            context.contentResolver.openInputStream(rom.uri)?.use {
                val zipStream = ZipInputStream(it)
                getNdsEntryInStream(zipStream)?.let {
                    ndsRomCache.cacheRom(rom) { fileOutputStream ->
                        val buffer = ByteArray(1024)

                        do {
                            val read = zipStream.read(buffer, 0, 1024)
                            if (read <= 0) {
                                break
                            }

                            fileOutputStream.write(buffer, 0, read)
                        } while (!emitter.isDisposed)

                        // If the operation was disposed, then the process did not runny successfully
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
            } ?: emitter.onError(CouldNotOpenZipFileException())
        }
    }
}