package me.magnum.melonds.impl

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import io.reactivex.Observable
import io.reactivex.subjects.PublishSubject
import me.magnum.melonds.domain.model.Rom
import me.magnum.melonds.domain.model.SizeUnit
import me.magnum.melonds.domain.repositories.SettingsRepository
import java.io.File
import java.io.FileOutputStream
import java.util.*

class NdsRomCache(private val context: Context, private val settingsRepository: SettingsRepository) {
    companion object {
        private const val ROMS_CACHE_DIR = "extracted_roms"
        private const val TEMP_FILE_NAME = "temp"
    }

    private class CouldNotCreateRomCacheDirectoryException : Exception("Failed to create ROM cache directory")

    private val cacheModifiedSubject = PublishSubject.create<Unit>()

    fun getCacheSize(): Observable<SizeUnit> {
        return cacheModifiedSubject.startWith(Unit).map { calculateCacheSize() }
    }

    fun clearCache(): Boolean {
        val romCacheDir = context.externalCacheDir?.let { File(it, ROMS_CACHE_DIR) } ?: return false
        val result = romCacheDir.deleteRecursively()
        cacheModifiedSubject.onNext(Unit)

        return result
    }

    fun getCachedRomFile(rom: Rom, forUse: Boolean = false): Uri? {
        val romHash = rom.uri.hashCode().toString()
        val romCacheDir = context.externalCacheDir?.let { File(it, ROMS_CACHE_DIR) }
        if (romCacheDir == null || !romCacheDir.isDirectory) {
            return null
        }

        val romFile = File(romCacheDir, romHash)
        return if (romFile.isFile) {
            if (forUse) {
                romFile.setLastModified(Date().time)
            }

            return DocumentFile.fromFile(romFile).uri
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
            val romFileSize = romExtractor.getExtractedRomFileSize()
            freeCacheSpaceIfRequired(romFileSize)

            tempCachedFile.outputStream().use {
                val success = romExtractor.saveRomFile(it)
                if (success) {
                    val romHash = rom.uri.hashCode().toString()
                    val cachedFile = File(romCacheDir, romHash)
                    tempCachedFile.renameTo(cachedFile)
                    cacheModifiedSubject.onNext(Unit)
                } else {
                    tempCachedFile.delete()
                }
            }
        } catch (e: Exception) {
            tempCachedFile.delete()
            throw e
        }
    }

    private fun calculateCacheSize(): SizeUnit {
        val romCacheDir = context.externalCacheDir?.let { File(it, ROMS_CACHE_DIR) }
        val cacheSize = romCacheDir?.listFiles()?.sumOf { file: File -> file.length() } ?: 0L
        return SizeUnit.Bytes(cacheSize)
    }

    private fun freeCacheSpaceIfRequired(requiredSize: SizeUnit) {
        val currentCacheSize = calculateCacheSize()
        val maxCacheSize = settingsRepository.getRomCacheMaxSize()

        val requiredCacheSize = currentCacheSize + requiredSize
        if (requiredCacheSize > maxCacheSize) {
            freeCacheSpace(requiredCacheSize - maxCacheSize)
        }
    }

    private fun freeCacheSpace(sizeToFree: SizeUnit) {
        val romCacheDir = context.externalCacheDir?.let { File(it, ROMS_CACHE_DIR) }
        // Use the last modified timestamp to determine the last time the ROM was used. The timestamp must be updated whenever the ROM is requested for use
        val romFilesByCreationDate = romCacheDir?.listFiles()?.sortedBy { file: File -> file.lastModified() } ?: return
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
        fun getExtractedRomFileSize(): SizeUnit

        /**
         * Requests the ROM to be extracted into the given [FileOutputStream]. This function should return true if the operation was successful or false if a problem occurred
         * or the operation was interrupted. If false is returned, the cached file is deleted.
         */
        fun saveRomFile(fileStream: FileOutputStream): Boolean
    }
}