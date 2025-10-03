package me.magnum.melonds.di

import android.content.Context
import coil.ImageLoader
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import me.magnum.melonds.impl.BackgroundThumbnailProvider
import me.magnum.melonds.impl.RomIconProvider
import me.magnum.melonds.impl.image.CoilBackgroundThumbnailFetcher
import me.magnum.melonds.impl.image.CoilRomIconFetcher
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object CoilModule {

    @Provides
    fun provideBackgroundThumbnailFetcher(backgroundThumbnailProvider: BackgroundThumbnailProvider): CoilBackgroundThumbnailFetcher.Factory {
        return CoilBackgroundThumbnailFetcher.Factory(backgroundThumbnailProvider)
    }

    @Provides
    fun provideRomIconFetcher(romIconProvider: RomIconProvider): CoilRomIconFetcher.Factory {
        return CoilRomIconFetcher.Factory(romIconProvider)
    }

    @Provides
    @Singleton
    fun provideCoilImageLoader(
        @ApplicationContext context: Context,
        coilBackgroundThumbnailFetcherFactory: CoilBackgroundThumbnailFetcher.Factory,
        coilRomIconFetcherFactory: CoilRomIconFetcher.Factory,
    ): ImageLoader {
        return ImageLoader.Builder(context)
            .components {
                add(coilBackgroundThumbnailFetcherFactory)
                add(coilRomIconFetcherFactory)
            }
            .crossfade(true)
            .build()
    }
}