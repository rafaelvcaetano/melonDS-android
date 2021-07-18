package me.magnum.melonds.common.romprocessors

import android.content.Context
import me.magnum.melonds.domain.model.SizeUnit
import me.magnum.melonds.impl.NdsRomCache
import org.apache.commons.compress.archivers.sevenz.SevenZArchiveEntry
import org.apache.commons.compress.archivers.sevenz.SevenZFile
import org.apache.commons.compress.utils.IOUtils
import org.apache.commons.compress.utils.SeekableInMemoryByteChannel
import java.io.InputStream

class SevenZRomFileProcessor(context: Context, ndsRomCache: NdsRomCache) : CompressedRomFileProcessor(context, ndsRomCache) {

    override fun getNdsEntryStreamInFileStream(fileStream: InputStream): RomFileStream? {
        val channel = SeekableInMemoryByteChannel(IOUtils.toByteArray(fileStream))
        val sevenZFile = SevenZFile(channel)
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