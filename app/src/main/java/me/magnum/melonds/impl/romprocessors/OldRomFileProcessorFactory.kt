package me.magnum.melonds.impl.romprocessors

import android.content.Context
import me.magnum.melonds.common.romprocessors.NdsRomFileProcessor
import me.magnum.melonds.common.romprocessors.RomFileProcessor
import me.magnum.melonds.common.romprocessors.ZipRomFileProcessor
import me.magnum.melonds.common.uridelegates.UriHandler
import me.magnum.melonds.impl.NdsRomCache

class OldRomFileProcessorFactory(context: Context, uriHandler: UriHandler, ndsRomCache: NdsRomCache) : BaseRomFileProcessorFactory(context) {
    private val prefixProcessorMap = mapOf(
        "nds" to NdsRomFileProcessor(context, uriHandler),
        "zip" to ZipRomFileProcessor(context, uriHandler, ndsRomCache)
    )

    override fun getRomFileProcessorForFileExtension(extension: String): RomFileProcessor? {
        return prefixProcessorMap[extension]
    }
}