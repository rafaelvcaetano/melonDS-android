package me.magnum.melonds.common.romprocessors

import android.content.Context
import me.magnum.melonds.common.uridelegates.UriHandler
import me.magnum.melonds.domain.model.SizeUnit
import me.magnum.melonds.impl.NdsRomCache
import java.io.InputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

class ZipRomFileProcessor(context: Context, uriHandler: UriHandler, ndsRomCache: NdsRomCache) : CompressedRomFileProcessor(context, uriHandler, ndsRomCache) {

    override fun getNdsEntryStreamInFileStream(fileStream: InputStream): RomFileStream? {
        val zipStream = ZipInputStream(fileStream)
        return try {
            val entry = getNdsEntryInZipStream(zipStream)
            if (entry == null) {
                zipStream.close()
                null
            } else {
                // ZIP ROMs seem to require the stream to be buffered, otherwise data is not processed properly
                RomFileStream(zipStream.buffered(), entry.size.takeIf { size -> size >= 0 }?.let { size -> SizeUnit.Bytes(size) })
            }
        } catch (error: Exception) {
            runCatching { zipStream.close() }
            throw error
        }
    }

    private fun getNdsEntryInZipStream(inputStream: ZipInputStream): ZipEntry? {
        val discard = ByteArray(8192)
        var entries = 0
        var discardedBytes = 0L
        do {
            val nextEntry = inputStream.nextEntry ?: break
            if (++entries > ArchiveReadBudget.MAX_ENTRIES) return null
            if (!nextEntry.isDirectory && isSupportedRomFile(nextEntry.name)) {
                if (nextEntry.size > ArchiveReadBudget.MAX_ROM_BYTES) return null
                return nextEntry
            }
            while (true) {
                val read = inputStream.read(discard)
                if (read <= 0) break
                discardedBytes += read
                if (discardedBytes > ArchiveReadBudget.MAX_DISCOVERY_BYTES) return null
            }
            inputStream.closeEntry()
        } while (true)
        return null
    }
}
