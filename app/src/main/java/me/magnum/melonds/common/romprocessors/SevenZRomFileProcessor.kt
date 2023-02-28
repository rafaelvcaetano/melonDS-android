package me.magnum.melonds.common.romprocessors

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.core.content.getSystemService
import me.magnum.melonds.common.uridelegates.UriHandler
import me.magnum.melonds.domain.model.SizeUnit
import me.magnum.melonds.impl.NdsRomCache
import org.apache.commons.compress.archivers.sevenz.SevenZArchiveEntry
import org.apache.commons.compress.archivers.sevenz.SevenZFile
import org.apache.commons.compress.archivers.sevenz.SevenZFileOptions
import java.io.FileInputStream
import java.io.InputStream

@RequiresApi(Build.VERSION_CODES.N)
class SevenZRomFileProcessor(private val context: Context, uriHandler: UriHandler, ndsRomCache: NdsRomCache) : CompressedRomFileProcessor(context, uriHandler, ndsRomCache) {

    override fun getNdsEntryStreamInFileStream(fileStream: InputStream): RomFileStream? {
        if (fileStream !is FileInputStream) {
            return null
        }

        val deviceMemory = context.getSystemService<ActivityManager>()?.let {
            val memoryInfo = ActivityManager.MemoryInfo()
            it.getMemoryInfo(memoryInfo)
            SizeUnit.Bytes(memoryInfo.totalMem)
        } ?: SizeUnit.Bytes(Int.MAX_VALUE.toLong())

        val options = SevenZFileOptions.builder()
            .withMaxMemoryLimitInKb((deviceMemory * 0.1f).toKB().toInt())
            .build()

        val sevenZFile = SevenZFile(fileStream.channel, options)
        return getNdsEntryInFile(sevenZFile)?.let {
            RomFileStream(sevenZFile.getInputStream(it), SizeUnit.Bytes(it.size))
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
}