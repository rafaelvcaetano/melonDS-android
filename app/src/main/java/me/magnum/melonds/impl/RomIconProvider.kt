package me.magnum.melonds.impl

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.magnum.melonds.common.romprocessors.RomFileProcessorFactory
import me.magnum.melonds.domain.model.Rom
import java.io.File
import java.util.*
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * Provider for ROM icons that supports caching. Both memory and disk caches are supported. If upon
 * request an icon is not found, it is generated and, if generated successfully, it's stored on both
 * caches.
 * The name of the file for the disk cache is the hash of the ROM's URI.
 */
class RomIconProvider(private val context: Context, private val romFileProcessorFactory: RomFileProcessorFactory) {
    companion object {
        private const val ICON_CACHE_DIR = "rom_icons"
    }

    private val memoryIconCache = mutableMapOf<String, Bitmap>()
    private val romIconLocks = Collections.synchronizedMap(mutableMapOf<String, ReentrantLock>())

    suspend fun getRomIcon(rom: Rom): Bitmap? = withContext(Dispatchers.IO) {
        val romHash = rom.uri.hashCode().toString()
        getRomIconLock(romHash).withLock {
            loadIconFromMemory(romHash, rom)
        }
    }

    fun clearIconCache() {
        memoryIconCache.clear()
        val iconCacheDir = getIconCacheDir() ?: return
        if (iconCacheDir.isDirectory) {
            iconCacheDir.deleteRecursively()
        }
    }

    private fun getRomIconLock(romHash: String): ReentrantLock {
        synchronized(romIconLocks) {
            return romIconLocks.getOrPut(romHash) {
                ReentrantLock()
            }
        }
    }

    private fun loadIconFromMemory(hash: String, rom: Rom): Bitmap? {
        var bitmap = memoryIconCache[hash]
        if (bitmap != null)
            return bitmap

        bitmap = loadIconFromDisk(hash, rom)
        if (bitmap != null)
            memoryIconCache[hash] = bitmap

        return bitmap
    }

    private fun loadIconFromDisk(hash: String, rom: Rom): Bitmap? {
        val iconCacheDir = getIconCacheDir()
        if (iconCacheDir?.isDirectory == true) {
            val iconFile = File(iconCacheDir, hash)
            if (iconFile.isFile) {
                return BitmapFactory.decodeFile(iconFile.absolutePath)
            }
        }

        val romDocument = DocumentFile.fromSingleUri(context, rom.uri) ?: return null
        val romProcessor = romFileProcessorFactory.getFileRomProcessorForDocument(romDocument) ?: return null
        val bitmap = romProcessor.getRomIcon(rom)
        if (bitmap != null && iconCacheDir != null) {
            saveRomIcon(hash, bitmap)
        }
        return bitmap
    }

    private fun saveRomIcon(romHash: String, icon: Bitmap) {
        val iconCacheDir = getIconCacheDir() ?: return
        if (iconCacheDir.isDirectory || iconCacheDir.mkdirs()) {
            val iconFile = File(iconCacheDir, romHash)
            try {
                iconFile.outputStream().use {
                    icon.compress(Bitmap.CompressFormat.PNG, 100, it)
                }
            } catch (_: Exception) {
                // Ignore errors
            }
        }
    }

    private fun getIconCacheDir(): File? {
        return context.externalCacheDir?.let { File(it, ICON_CACHE_DIR) }
    }
}