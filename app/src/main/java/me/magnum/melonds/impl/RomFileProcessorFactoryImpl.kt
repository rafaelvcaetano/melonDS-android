package me.magnum.melonds.impl

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import dagger.Lazy
import me.magnum.melonds.common.romprocessors.RomFileProcessor
import me.magnum.melonds.common.romprocessors.RomFileProcessorFactory
import me.magnum.melonds.common.romprocessors.NdsRomFileProcessor
import me.magnum.melonds.common.romprocessors.ZipRomFileProcessor
import me.magnum.melonds.utils.RomIconProvider
import javax.inject.Inject


class RomFileProcessorFactoryImpl(
        private val context: Context,
        @Inject val romIconProvider: Lazy<RomIconProvider>,
        ndsRomCache: NdsRomCache
)  : RomFileProcessorFactory {
    private val prefixProcessorMap = mapOf(
            "nds" to NdsRomFileProcessor(context),
            "zip" to ZipRomFileProcessor(context, romIconProvider, ndsRomCache)
    )

    override fun getFileRomProcessorForDocument(romDocument: DocumentFile): RomFileProcessor? {
        val fileName = romDocument.name ?: return null
        val lastDotIndex = fileName.lastIndexOf('.')
        if (lastDotIndex < 0) return null

        val extension = fileName.substring(lastDotIndex + 1)
        return prefixProcessorMap[extension]
    }

    override fun getFileRomProcessorForDocument(romUri: Uri): RomFileProcessor? {
        val romDocument = DocumentFile.fromSingleUri(context, romUri) ?: return null
        return getFileRomProcessorForDocument(romDocument)
    }
}