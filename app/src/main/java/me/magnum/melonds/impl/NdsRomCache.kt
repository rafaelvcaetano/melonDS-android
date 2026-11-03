package me.magnum.melonds.impl

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.receiveAsFlow
import me.magnum.melonds.domain.model.rom.Rom
import me.magnum.melonds.domain.model.SizeUnit
import me.magnum.melonds.domain.repositories.SettingsRepository
import java.io.File
import java.io.FileOutputStream
import java.io.FilterOutputStream
import java.io.IOException
import java.io.OutputStream
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.*

class NdsRomCache(private val context: Context, private val settingsRepository: SettingsRepository) {
    companion object {
        private const val ROMS_CACHE_DIR = "extracted_roms"
        private const val TEMP_FILE_NAME = "temp"
    }

    private class CouldNotCreateRomCacheDirectoryException : Exception("Failed to create ROM cache directory")

    private val cacheModifiedSubject = Channel<Unit>(Channel.CONFLATED)

    fun getCacheSizeFlow(): Flow<SizeUnit> {
        return cacheModifiedSubject.receiveAsFlow()
            .onStart { emit(Unit) }
            .map { calculateCacheSize() }
    }

    fun clearCache(): Boolean {
        val romCacheDir = context.externalCacheDir?.let { File(it, ROMS_CACHE_DIR) } ?: return false
        val result = romCacheDir.deleteRecursively()
        cacheModifiedSubject.trySend(Unit)

        return result
    }

    fun getCachedRomFile(rom: Rom, forUse: Boolean = false): Uri? {
        val romHash = cacheKey(rom)
        val romCacheDir = context.externalCacheDir?.let { File(it, ROMS_CACHE_DIR) }
        if (romCacheDir == null || !romCacheDir.isDirectory) {
            return null
        }

        val romFile = File(romCacheDir, romHash)
        return if (romFile.isFile) {
            if (forUse) {
                romFile.setLastModified(Date().time)
            }

            DocumentFile.fromFile(romFile).uri
        } else {
            null
        }
    }

    /**
     * Caches the file of the provided ROM. The caller is responsible for copying the contents of the file into the [FileOutputStream] provided by the [romExtractor].
     *
     * @param rom The ROM whose file will be cached
     * @param romExtractor The handler that caller should use to provided the contents of the ROM's file. The callback should return true if the operation was successful
     * or false if a problem occurred or the operation was interrupted. If false is returned, the cached file is deleted
     */
    fun cacheRom(rom: Rom, romExtractor: RomExtractor) {
        val romCacheDir = context.externalCacheDir?.let { File(it, ROMS_CACHE_DIR) }
        if (romCacheDir == null || !(romCacheDir.isDirectory || romCacheDir.mkdirs())) {
            throw CouldNotCreateRomCacheDirectoryException()
        }

        val tempCachedFile = File(romCacheDir, TEMP_FILE_NAME)

        try {
            tempCachedFile.delete()
            val declaredRomFileSize = romExtractor.getExtractedRomFileSize()
            val maxCacheSize = settingsRepository.getRomCacheMaxSize()
            if (maxCacheSize.toBytes() < 0 || (declaredRomFileSize != null && declaredRomFileSize.toBytes() < 0)) {
                throw IOException("Invalid ROM cache size")
            }
            if (declaredRomFileSize != null && declaredRomFileSize > maxCacheSize) {
                throw IOException("ROM is larger than the configured cache limit")
            }
            val extractionLimit = declaredRomFileSize ?: maxCacheSize

            tempCachedFile.outputStream().use { fileStream ->
                val success = romExtractor.saveRomFile(
                    BoundedOutputStream(fileStream, extractionLimit.toBytes()) { projectedSize ->
                        ensureCacheBudgetForTemp(projectedSize, tempCachedFile, maxCacheSize.toBytes())
                    },
                )
                if (success) {
                    // Unknown-size ZIP entries are extracted transactionally first.
                    // Evict only the space actually needed, and never erase a valid
                    // cache merely because the archive omitted its size metadata.
                    freeCacheSpaceIfRequired(SizeUnit.Bytes(tempCachedFile.length()), tempCachedFile)
                    val romHash = cacheKey(rom)
                    val cachedFile = File(romCacheDir, romHash)
                    if ((cachedFile.exists() && !cachedFile.delete()) || !tempCachedFile.renameTo(cachedFile)) {
                        throw IOException("Failed to publish extracted ROM cache entry")
                    }
                    cacheModifiedSubject.trySend(Unit)
                } else {
                    tempCachedFile.delete()
                }
            }
        } catch (e: Exception) {
            tempCachedFile.delete()
            throw e
        }
    }

    private fun calculateCacheSize(excludedFile: File? = null): SizeUnit {
        val romCacheDir = context.externalCacheDir?.let { File(it, ROMS_CACHE_DIR) }
        val cacheSize = romCacheDir?.listFiles()
            ?.filterNot { it == excludedFile }
            ?.sumOf { file: File -> file.length() } ?: 0L
        return SizeUnit.Bytes(cacheSize)
    }

    private fun freeCacheSpaceIfRequired(requiredSize: SizeUnit, excludedFile: File? = null) {
        val currentCacheSize = calculateCacheSize(excludedFile)
        val maxCacheSize = settingsRepository.getRomCacheMaxSize()

        val requiredCacheSize = currentCacheSize + requiredSize
        if (requiredCacheSize > maxCacheSize) {
            freeCacheSpace(requiredCacheSize - maxCacheSize, excludedFile)
        }
    }

    private fun ensureCacheBudgetForTemp(projectedTempSize: Long, tempFile: File, maxCacheBytes: Long) {
        val currentBytes = calculateCacheSize(tempFile).toBytes()
        if (currentBytes > maxCacheBytes - projectedTempSize) {
            freeCacheSpace(SizeUnit.Bytes(currentBytes - (maxCacheBytes - projectedTempSize)), tempFile)
        }

        val remainingBytes = calculateCacheSize(tempFile).toBytes()
        if (remainingBytes > maxCacheBytes - projectedTempSize) {
            throw IOException("Unable to keep ROM extraction within the configured cache limit")
        }
    }

    private fun freeCacheSpace(sizeToFree: SizeUnit, excludedFile: File? = null) {
        val romCacheDir = context.externalCacheDir?.let { File(it, ROMS_CACHE_DIR) }
        // Use the last modified timestamp to determine the last time the ROM was used. The timestamp must be updated whenever the ROM is requested for use
        val romFilesByCreationDate = romCacheDir?.listFiles()
            ?.filterNot { it == excludedFile }
            ?.sortedBy { file: File -> file.lastModified() } ?: return
        var freedBytes = SizeUnit.Bytes(0)

        for (file in romFilesByCreationDate) {
            freedBytes += file.length()
            file.delete()

            if (freedBytes >= sizeToFree) {
                break
            }
        }
    }

    interface RomExtractor {
        /**
         * Returns the uncompressed size of the ROM file to be extracted.
         */
        fun getExtractedRomFileSize(): SizeUnit?

        /**
         * Requests the ROM to be extracted into the given [FileOutputStream]. This function should return true if the operation was successful or false if a problem occurred
         * or the operation was interrupted. If false is returned, the cached file is deleted.
         */
        fun saveRomFile(fileStream: OutputStream): Boolean
    }

    private fun cacheKey(rom: Rom): String {
        return MessageDigest.getInstance("SHA-256")
            .digest(rom.uri.toString().toByteArray(StandardCharsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }

    private class BoundedOutputStream(
        output: OutputStream,
        private val limit: Long,
        private val beforeWrite: (projectedSize: Long) -> Unit,
    ) : FilterOutputStream(output) {
        private var written = 0L

        override fun write(value: Int) {
            requireCapacity(1)
            beforeWrite(written + 1)
            out.write(value)
            written++
        }

        override fun write(buffer: ByteArray, offset: Int, length: Int) {
            if (offset < 0 || length < 0 || length > buffer.size - offset) throw IndexOutOfBoundsException()
            requireCapacity(length.toLong())
            beforeWrite(written + length)
            out.write(buffer, offset, length)
            written += length
        }

        private fun requireCapacity(count: Long) {
            if (count > limit - written) throw IOException("Extracted ROM exceeds its declared size")
        }
    }
}
