package me.magnum.melonds.common.romprocessors

import android.content.Context
import me.magnum.melonds.impl.NdsRomCache
import java.io.InputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

class ZipRomFileProcessor(context: Context, ndsRomCache: NdsRomCache) : CompressedRomFileProcessor(context, ndsRomCache) {
    override fun getNdsEntryStreamInFileStream(fileStream: InputStream): RomFileStream? {
        val zipStream = ZipInputStream(fileStream)
        return getNdsEntryInZipStream(zipStream)?.let {
            RomFileStream(zipStream, it.size)
        }
    }

    private fun getNdsEntryInZipStream(inputStream: ZipInputStream): ZipEntry? {
        do {
            val nextEntry = inputStream.nextEntry ?: break
            if (!nextEntry.isDirectory && nextEntry.name.lowercase().endsWith(".nds")) {
                return nextEntry
            }
        } while (true)
        return null
    }
}