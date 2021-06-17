package me.magnum.melonds.impl

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import io.reactivex.Observable
import io.reactivex.subjects.PublishSubject
import me.magnum.melonds.domain.model.Rom
import java.io.File
import java.io.FileOutputStream

class NdsRomCache(private val context: Context) {
    companion object {
        private const val ROMS_CACHE_DIR = "extracted_roms"
        private const val TEMP_FILE_NAME = "temp"
    }

    private class CouldNotCreateRomCacheDirectoryException : Exception("Failed to create ROM cache directory")

    private val cacheModifiedSubject = PublishSubject.create<Unit>()

    fun getCacheSize(): Observable<Long> {
        return cacheModifiedSubject.startWith(Unit).map {
            val romCacheDir = context.externalCacheDir?.let { File(it, ROMS_CACHE_DIR) }
            val cacheSize = romCacheDir?.listFiles()?.sumOf { file: File -> file.length() } ?: 0L
            cacheSize
        }
    }

    fun clearCache(): Boolean {
        val romCacheDir = context.externalCacheDir?.let { File(it, ROMS_CACHE_DIR) } ?: return false
        val result = romCacheDir.deleteRecursively()
        cacheModifiedSubject.onNext(Unit)

        return result
    }

    fun getCachedRomFile(rom: Rom): Uri? {
        val romHash = rom.uri.hashCode().toString()
        val romCacheDir = context.externalCacheDir?.let { File(it, ROMS_CACHE_DIR) }
        if (romCacheDir == null || !romCacheDir.isDirectory) {
            return null
        }

        val romFile = File(romCacheDir, romHash)
        return if (romFile.isFile) {
            return DocumentFile.fromFile(romFile).uri
        } else {
            null
        }
    }

    /**
     * Caches the file of the provided ROM. The caller is responsible for copying the contents of the file into the [FileOutputStream] provided in the [runner].
     *
     * @param rom The ROM whose file will be cached
     * @param runner The callback with that the caller should use to provided the contents of the ROM's file. The callback should return true if the operation was successful
     * or false if a problem occurred or the operation was interrupted. If false is returned, the cached file is deleted
     */
    fun cacheRom(rom: Rom, runner: (FileOutputStream) -> Boolean) {
        val romCacheDir = context.externalCacheDir?.let { File(it, ROMS_CACHE_DIR) }
        if (romCacheDir == null || !(romCacheDir.isDirectory || romCacheDir.mkdirs())) {
            throw CouldNotCreateRomCacheDirectoryException()
        }

        val tempCachedFile = File(romCacheDir, TEMP_FILE_NAME)

        try {
            tempCachedFile.outputStream().use {
                val success = runner(it)
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
}