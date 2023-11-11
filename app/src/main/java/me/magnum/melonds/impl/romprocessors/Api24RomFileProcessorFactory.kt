package me.magnum.melonds.impl.romprocessors

import android.content.Context
import android.os.Build
import androidx.annotation.RequiresApi
import me.magnum.melonds.common.romprocessors.NdsRomFileProcessor
import me.magnum.melonds.common.romprocessors.RomFileProcessor
import me.magnum.melonds.common.romprocessors.SevenZRomFileProcessor
import me.magnum.melonds.common.romprocessors.ZipRomFileProcessor
import me.magnum.melonds.common.uridelegates.UriHandler
import me.magnum.melonds.impl.NdsRomCache

@RequiresApi(Build.VERSION_CODES.N)
class Api24RomFileProcessorFactory(private val context: Context, private val uriHandler: UriHandler, private val ndsRomCache: NdsRomCache) : BaseRomFileProcessorFactory(context) {

    private val prefixProcessorMap: Map<String, RomFileProcessor>

    init {
        val ndsRomFileProcessor = NdsRomFileProcessor(context, uriHandler)
        prefixProcessorMap = mapOf(
            "nds" to ndsRomFileProcessor,
            "dsi" to ndsRomFileProcessor,
            "ids" to ndsRomFileProcessor,
            "zip" to ZipRomFileProcessor(context, uriHandler, ndsRomCache),
            "7z" to SevenZRomFileProcessor(context, uriHandler, ndsRomCache)
        )
    }

    override fun getRomFileProcessorForFileExtension(extension: String): RomFileProcessor? {
        return prefixProcessorMap[extension]
    }
}