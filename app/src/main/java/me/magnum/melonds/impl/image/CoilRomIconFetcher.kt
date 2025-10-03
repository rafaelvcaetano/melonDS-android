package me.magnum.melonds.impl.image

import androidx.core.graphics.drawable.toDrawable
import coil.ImageLoader
import coil.decode.DataSource
import coil.fetch.DrawableResult
import coil.fetch.FetchResult
import coil.fetch.Fetcher
import coil.request.Options
import me.magnum.melonds.domain.model.rom.Rom
import me.magnum.melonds.impl.RomIconProvider

class CoilRomIconFetcher(
    private val romIconProvider: RomIconProvider,
    private val options: Options,
    private val rom: Rom,
) : Fetcher {

    override suspend fun fetch(): FetchResult? {
        val romIcon = romIconProvider.getRomIcon(rom)
        return romIcon?.let {
            DrawableResult(
                drawable = it.toDrawable(options.context.resources),
                isSampled = false,
                dataSource = DataSource.MEMORY,
            )
        }
    }

    class Factory(private val romIconProvider: RomIconProvider) : Fetcher.Factory<Rom> {
        override fun create(data: Rom, options: Options, imageLoader: ImageLoader): Fetcher? {
            return CoilRomIconFetcher(romIconProvider, options, data)
        }
    }
}