package me.magnum.melonds.common.romprocessors

import android.app.ActivityManager
import android.content.Context
import android.util.Log
import androidx.core.content.getSystemService
import me.magnum.melonds.common.uridelegates.UriHandler
import me.magnum.melonds.domain.model.SizeUnit
import me.magnum.melonds.impl.NdsRomCache
import org.apache.commons.compress.archivers.sevenz.SevenZArchiveEntry
import org.apache.commons.compress.archivers.sevenz.SevenZFile
import java.io.FileInputStream
import java.io.InputStream

class SevenZRomFileProcessor(private val context: Context, uriHandler: UriHandler, ndsRomCache: NdsRomCache) : CompressedRomFileProcessor(context, uriHandler, ndsRomCache) {

    companion object {
        private const val TAG = "SevenZRomFileProcessor"
    }

    override fun getNdsEntryStreamInFileStream(fileStream: InputStream): RomFileStream? {
        if (fileStream !is FileInputStream) {
            return null
        }

        val deviceMemory = context.getSystemService<ActivityManager>()?.let {
            val memoryInfo = ActivityManager.MemoryInfo()
            it.getMemoryInfo(memoryInfo)
            SizeUnit.Bytes(memoryInfo.totalMem)
        } ?: SizeUnit.Bytes(Int.MAX_VALUE.toLong())

        try {
            val sevenZFile = SevenZFile.Builder()
                .setMaxMemoryLimitKb((deviceMemory * 0.1f).toKB().toInt())
                .setSeekableByteChannel(fileStream.channel)
                .get()
            return getNdsEntryInFile(sevenZFile)?.let {
                RomFileStream(sevenZFile.getInputStream(it), SizeUnit.Bytes(it.size))
            }
        }
        catch (e: OutOfMemoryError) {
            Log.e(TAG, "Failed to load 7z ROM contents", e)
        }

        return null
    }

    private fun getNdsEntryInFile(sevenZFile: SevenZFile): SevenZArchiveEntry? {
        do {
            val nextEntry = sevenZFile.nextEntry ?: break
            if (!nextEntry.isDirectory && isSupportedRomFile(nextEntry.name)) {
                return nextEntry
            }
        } while (true)
        return null
    }
}