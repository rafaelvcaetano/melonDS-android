package me.magnum.melonds.di

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ApplicationComponent
import dagger.hilt.android.qualifiers.ApplicationContext
import me.magnum.melonds.database.MelonDatabase
import me.magnum.melonds.domain.repositories.CheatsRepository
import me.magnum.melonds.domain.repositories.LayoutsRepository
import me.magnum.melonds.domain.repositories.RomsRepository
import me.magnum.melonds.domain.repositories.SettingsRepository
import me.magnum.melonds.impl.*
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
    fun provideRomsRepository(@ApplicationContext context: Context, gson: Gson, settingsRepository: SettingsRepository, fileRomProcessorFactory: FileRomProcessorFactory): RomsRepository {
        return FileSystemRomsRepository(context, gson, settingsRepository, fileRomProcessorFactory)
    }

    @Provides
    @Singleton
    fun provideCheatsRepository(@ApplicationContext context: Context, database: MelonDatabase): CheatsRepository {
        return RoomCheatsRepository(context, database)
    }

    @Provides
    @Singleton
    fun provideNdsRomCache(@ApplicationContext context: Context): NdsRomCache {
        return NdsRomCache(context)
    }

    @Provides
    @Singleton
    fun provideLayoutsRepository(@ApplicationContext context: Context, gson: Gson, defaultLayoutBuilder: DefaultLayoutBuilder): LayoutsRepository {
        return InternalLayoutsRepository(context, gson, defaultLayoutBuilder)
    }

    @Provides
    @Singleton
    fun provideFileRomProcessorFactory(@ApplicationContext context: Context, ndsRomCache: NdsRomCache): FileRomProcessorFactory {
        return FileRomProcessorFactoryImpl(context, ndsRomCache)
    }

    @Provides
    @Singleton
    fun provideRomIconProvider(@ApplicationContext context: Context, fileRomProcessorFactory: FileRomProcessorFactory): RomIconProvider {
        return RomIconProvider(context, fileRomProcessorFactory)
    }

    @Provides
    @Singleton
    fun provideScreenUnitsConverter(@ApplicationContext context: Context): ScreenUnitsConverter {
        return ScreenUnitsConverter(context)
    }

    @Provides
    @Singleton
    fun provideDefaultLayoutBuilder(@ApplicationContext context: Context, screenUnitsConverter: ScreenUnitsConverter): DefaultLayoutBuilder {
        return DefaultLayoutBuilder(context, screenUnitsConverter)
    }
}