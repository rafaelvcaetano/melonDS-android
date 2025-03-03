package me.magnum.melonds.impl.image

import androidx.core.graphics.drawable.toDrawable
import coil.ImageLoader
import coil.decode.DataSource
import coil.fetch.DrawableResult
import coil.fetch.FetchResult
import coil.fetch.Fetcher
import coil.request.Options
import me.magnum.melonds.domain.model.Background
import me.magnum.melonds.impl.BackgroundThumbnailProvider

class CoilBackgroundThumbnailFetcher(
    private val backgroundThumbnailProvider: BackgroundThumbnailProvider,
    private val background: Background,
    private val options: Options,
) : Fetcher {

    override suspend fun fetch(): FetchResult? {
        val backgroundThumbnail = backgroundThumbnailProvider.getBackgroundThumbnail(background)
        return backgroundThumbnail?.toDrawable(options.context.resources)?.let {
            DrawableResult(
                drawable = it,
                isSampled = false,
                dataSource = DataSource.DISK,
            )
        }
    }

    class Factory(private val backgroundThumbnailProvider: BackgroundThumbnailProvider) : Fetcher.Factory<Background> {
        override fun create(data: Background, options: Options, imageLoader: ImageLoader): Fetcher? {
            return CoilBackgroundThumbnailFetcher(backgroundThumbnailProvider, data, options)
        }
    }
}