package me.magnum.melonds.di

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ApplicationComponent
import dagger.hilt.android.qualifiers.ApplicationContext
import me.magnum.melonds.domain.repositories.RomsRepository
import me.magnum.melonds.domain.repositories.SettingsRepository
import me.magnum.melonds.impl.FileSystemRomsRepository
import me.magnum.melonds.impl.SharedPreferencesSettingsRepository
import me.magnum.melonds.utils.RomIconProvider
import javax.inject.Singleton

@Module
@InstallIn(ApplicationComponent::class)
object MelonModule {
    @Provides
    @Singleton
    fun provideSettingsRepository(@ApplicationContext context: Context, sharedPreferences: SharedPreferences, gson: Gson): SettingsRepository {
        return SharedPreferencesSettingsRepository(context, sharedPreferences, gson)
    }

    @Provides
    @Singleton
    fun provideRomsRepository(@ApplicationContext context: Context, gson: Gson, settingsRepository: SettingsRepository): RomsRepository {
        return FileSystemRomsRepository(context, gson, settingsRepository)
    }

    @Provides
    @Singleton
    fun provideRomIconProvider(@ApplicationContext context: Context): RomIconProvider {
        return RomIconProvider(context)
    }
}