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
    }

    private val cacheModifiedSubject = PublishSubject.create<Unit>()

    fun getCacheSize(): Observable<Long> {
        return cacheModifiedSubject.startWith(Unit).flatMap {
            Observable.create<Long> { emitter ->
                val romCacheDir = context.externalCacheDir?.let { File(it, ROMS_CACHE_DIR) }
                if (romCacheDir == null) {
                    emitter.onNext(0)
                } else {
                    val cacheSize = romCacheDir.listFiles()?.sumOf { file: File -> file.length() } ?: 0L
                    emitter.onNext(cacheSize)
                }
            }
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

    fun cacheRom(rom: Rom, runner: (FileOutputStream) -> Unit) {
        val romHash = rom.uri.hashCode().toString()
        val romCacheDir = context.externalCacheDir?.let { File(it, ROMS_CACHE_DIR) }
        val cachedFile = File(romCacheDir, romHash)

        try {
            FileOutputStream(cachedFile).use {
                runner(it)
                cacheModifiedSubject.onNext(Unit)
            }
        } catch (e: Exception) {
            cachedFile.delete()
            throw e
        }
    }
}