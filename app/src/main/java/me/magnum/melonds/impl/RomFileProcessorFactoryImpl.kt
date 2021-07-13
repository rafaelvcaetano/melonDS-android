package me.magnum.melonds.impl

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import me.magnum.melonds.common.romprocessors.*

class RomFileProcessorFactoryImpl(private val context: Context, ndsRomCache: NdsRomCache) : RomFileProcessorFactory {
    private val prefixProcessorMap = mapOf(
            "nds" to NdsRomFileProcessor(context),
            "zip" to ZipRomFileProcessor(context, ndsRomCache),
            "7z" to SevenZRomFileProcessor(context, ndsRomCache)
    )

    override fun getFileRomProcessorForDocument(romDocument: DocumentFile): RomFileProcessor? {
        val fileName = romDocument.name ?: return null
        val lastDotIndex = fileName.lastIndexOf('.')
        if (lastDotIndex < 0) return null

        val extension = fileName.substring(lastDotIndex + 1).lowercase()
        return prefixProcessorMap[extension]
    }

    override fun getFileRomProcessorForDocument(romUri: Uri): RomFileProcessor? {
        val romDocument = DocumentFile.fromSingleUri(context, romUri) ?: return null
        return getFileRomProcessorForDocument(romDocument)
    }
}