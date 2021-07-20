package me.magnum.melonds.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import me.magnum.melonds.domain.repositories.UpdatesRepository
import me.magnum.melonds.playstore.PlayStoreUpdatesRepository
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object PlayStoreModule {
    @Provides
    @Singleton
    fun provideUpdatesRepository(): UpdatesRepository {
        return PlayStoreUpdatesRepository()
    }
}