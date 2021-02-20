package me.magnum.melonds.impl.romprocessors

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import io.reactivex.Single
import me.magnum.melonds.domain.model.Rom
import me.magnum.melonds.domain.model.RomConfig
import me.magnum.melonds.domain.model.RomInfo
import me.magnum.melonds.impl.FileRomProcessor
import me.magnum.melonds.impl.NdsRomCache
import me.magnum.melonds.utils.RomProcessor
import java.io.*
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

class ZipRomProcessor(private val context: Context, private val ndsRomCache: NdsRomCache) : FileRomProcessor {
    companion object {
        private const val EXTRACTED_ROMS_CACHE_DIR = "extracted_roms"
    }

    private class CouldNotCreateRomCacheDirectoryException : Exception("Failed to create ROM cache directory")
    private class CouldNotOpenZipFileException : Exception("Failed to open ZIP file for extraction")
    private class CouldNotFindNdsRomException : Exception("Failed to find an NDS ROM to extract")

    override fun getRomFromUri(uri: Uri): Rom? {
        return try {
            context.contentResolver.openInputStream(uri)?.use {
                val zipStream = ZipInputStream(it)
                getNdsEntryInStream(zipStream)?.let {
                    getRomNameInZipEntry(zipStream)?.let { romName ->
                        Rom(romName, uri, RomConfig())
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
            context.contentResolver.openInputStream(rom.uri)?.use {
                val zipStream = ZipInputStream(it)
                if (getNdsEntryInStream(zipStream) != null) {
                    zipStream
                } else {
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

    private fun getRomNameInZipEntry(inputStream: ZipInputStream): String? {
        return RomProcessor.getRomName(inputStream)
    }

    private fun extractRomFile(rom: Rom): Single<Uri> {
        return Single.create { emitter ->
            val romCacheDir = context.externalCacheDir?.let { File(it, EXTRACTED_ROMS_CACHE_DIR) }
            if (romCacheDir == null || !(romCacheDir.isDirectory || romCacheDir.mkdirs())) {
                emitter.onError(CouldNotCreateRomCacheDirectoryException())
            } else {
                val romHash = rom.uri.hashCode().toString()
                val cachedFile = File(romCacheDir, romHash)

                context.contentResolver.openInputStream(rom.uri)?.use {
                    val zipStream = ZipInputStream(it)
                    getNdsEntryInStream(zipStream)?.let {
                        ndsRomCache.cacheRom(rom) { fileOutputStream ->
                            val bufferedInputStream = BufferedInputStream(zipStream)
                            val bufferedOutputStream = BufferedOutputStream(fileOutputStream)
                            val buffer = ByteArray(1024)

                            do {
                                val read = bufferedInputStream.read(buffer, 0, 1024)
                                if (read <= 0) {
                                    break
                                }

                                bufferedOutputStream.write(buffer, 0, read)
                            } while (true)

                            emitter.onSuccess(DocumentFile.fromFile(cachedFile).uri)
                        }
                    } ?: emitter.onError(CouldNotFindNdsRomException())
                } ?: emitter.onError(CouldNotOpenZipFileException())
            }
        }
    }
}