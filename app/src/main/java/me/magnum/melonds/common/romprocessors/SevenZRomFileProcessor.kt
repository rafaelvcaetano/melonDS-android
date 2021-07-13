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
import org.apache.commons.compress.archivers.sevenz.SevenZArchiveEntry
import org.apache.commons.compress.archivers.sevenz.SevenZFile
import org.apache.commons.compress.utils.IOUtils
import org.apache.commons.compress.utils.SeekableInMemoryByteChannel
import java.io.InputStream

class SevenZRomFileProcessor(private val context: Context, private val ndsRomCache: NdsRomCache) : RomFileProcessor {
    private class CouldNotOpenSevenZFileException : Exception("Failed to open 7z file for extraction")
    private class CouldNotFindNdsRomException : Exception("Failed to find an NDS ROM to extract")
    private class CouldNotFindExtractedFileException : Exception("Failed to find extracted NDS ROM file")

    override fun getRomFromUri(romUri: Uri, parentUri: Uri): Rom? {
        return try {
            context.contentResolver.openInputStream(romUri)?.use {
                val channel = SeekableInMemoryByteChannel(IOUtils.toByteArray(it))
                SevenZFile(channel).use { sevenZFile ->
                    getNdsEntryInFile(sevenZFile)?.let { ndsRomEntry ->
                        val romName = getRomNameInZipEntry(sevenZFile.getInputStream(ndsRomEntry))
                        Rom(romName, romUri, parentUri, RomConfig())
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
                val channel = SeekableInMemoryByteChannel(IOUtils.toByteArray(it))
                SevenZFile(channel).use { sevenZFile ->
                    val ndsEntry = getNdsEntryInFile(sevenZFile)
                    if (ndsEntry != null) {
                        sevenZFile.getInputStream(ndsEntry)
                    } else {
                        sevenZFile.close()
                        null
                    }
                }
            }
        }
    }

    private fun getNdsEntryInFile(sevenZFile: SevenZFile): SevenZArchiveEntry? {
        do {
            val nextEntry = sevenZFile.nextEntry ?: break
            if (!nextEntry.isDirectory && nextEntry.name.lowercase().endsWith(".nds")) {
                return nextEntry
            }
        } while (true)
        return null
    }

    private fun getRomNameInZipEntry(inputStream: InputStream): String {
        return RomProcessor.getRomName(inputStream.buffered())
    }

    private fun extractRomFile(rom: Rom): Single<Uri> {
        return Single.create { emitter ->
            context.contentResolver.openInputStream(rom.uri)?.use {
                val channel = SeekableInMemoryByteChannel(IOUtils.toByteArray(it))
                SevenZFile(channel).use { sevenZFile ->
                    getNdsEntryInFile(sevenZFile)?.let { ndsRomEntry ->
                        sevenZFile.getInputStream(ndsRomEntry).use { romFileStream ->
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
                        }
                    } ?: emitter.onError(CouldNotFindNdsRomException())
                }
            } ?: emitter.onError(CouldNotOpenSevenZFileException())
        }
    }
}