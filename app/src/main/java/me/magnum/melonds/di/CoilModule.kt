package me.magnum.melonds.di

import android.content.Context
import coil.ImageLoader
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import me.magnum.melonds.impl.BackgroundThumbnailProvider
import me.magnum.melonds.impl.image.CoilBackgroundThumbnailFetcher
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object CoilModule {

    @Provides
    fun provideBackgroundThumbnailFetcher(backgroundThumbnailProvider: BackgroundThumbnailProvider): CoilBackgroundThumbnailFetcher.Factory {
        return CoilBackgroundThumbnailFetcher.Factory(backgroundThumbnailProvider)
    }

    @Provides
    @Singleton
    fun provideCoilImageLoader(@ApplicationContext context: Context, coilBackgroundThumbnailFetcherFactory: CoilBackgroundThumbnailFetcher.Factory): ImageLoader {
        return ImageLoader.Builder(context)
            .components {
                add(coilBackgroundThumbnailFetcherFactory)
            }
            .crossfade(true)
            .build()
    }
}