package me.magnum.melonds.di

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.os.Vibrator
import androidx.core.content.getSystemService
import com.google.gson.Gson
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import me.magnum.melonds.common.romprocessors.RomFileProcessorFactory
import me.magnum.melonds.common.uridelegates.UriHandler
import me.magnum.melonds.common.vibration.Api26VibratorDelegate
import me.magnum.melonds.common.vibration.OldVibratorDelegate
import me.magnum.melonds.common.vibration.TouchVibrator
import me.magnum.melonds.database.MelonDatabase
import me.magnum.melonds.domain.repositories.*
import me.magnum.melonds.domain.services.ConfigurationDirectoryVerifier
import me.magnum.melonds.impl.*
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object MelonModule {
    @Provides
    @Singleton
    fun provideSettingsRepository(@ApplicationContext context: Context, sharedPreferences: SharedPreferences, gson: Gson, uriHandler: UriHandler): SettingsRepository {
        return SharedPreferencesSettingsRepository(context, sharedPreferences, gson, uriHandler)
    }

    @Provides
    @Singleton
    fun provideRomsRepository(@ApplicationContext context: Context, gson: Gson, settingsRepository: SettingsRepository, romFileProcessorFactory: RomFileProcessorFactory): RomsRepository {
        return FileSystemRomsRepository(context, gson, settingsRepository, romFileProcessorFactory)
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
    fun provideLayoutsRepository(@ApplicationContext context: Context, gson: Gson, defaultLayoutProvider: DefaultLayoutProvider): LayoutsRepository {
        return InternalLayoutsRepository(context, gson, defaultLayoutProvider)
    }

    @Provides
    @Singleton
    fun provideBackgroundsRepository(@ApplicationContext context: Context, gson: Gson): BackgroundRepository {
        return InternalBackgroundsRepository(context, gson)
    }

    @Provides
    @Singleton
    fun provideConfigurationDirectoryVerifier(@ApplicationContext context: Context, settingsRepository: SettingsRepository): ConfigurationDirectoryVerifier {
        return FileSystemConfigurationDirectoryVerifier(context, settingsRepository)
    }

    @Provides
    @Singleton
    fun provideFileRomProcessorFactory(@ApplicationContext context: Context, ndsRomCache: NdsRomCache): RomFileProcessorFactory {
        return RomFileProcessorFactoryImpl(context, ndsRomCache)
    }

    @Provides
    @Singleton
    fun provideRomIconProvider(@ApplicationContext context: Context, romFileProcessorFactory: RomFileProcessorFactory): RomIconProvider {
        return RomIconProvider(context, romFileProcessorFactory)
    }

    @Provides
    @Singleton
    fun provideBackgroundThumbnailProvider(@ApplicationContext context: Context): BackgroundThumbnailProvider {
        return BackgroundThumbnailProvider(context)
    }

    @Provides
    @Singleton
    fun provideTouchVibrator(@ApplicationContext context: Context, settingsRepository: SettingsRepository): TouchVibrator {
        val vibrator = context.getSystemService<Vibrator>()!!
        val delegate = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Api26VibratorDelegate(vibrator)
        } else {
            OldVibratorDelegate(vibrator)
        }
        return TouchVibrator(delegate, settingsRepository)
    }

    @Provides
    @Singleton
    fun provideScreenUnitsConverter(@ApplicationContext context: Context): ScreenUnitsConverter {
        return ScreenUnitsConverter(context)
    }

    @Provides
    @Singleton
    fun provideDefaultLayoutBuilder(@ApplicationContext context: Context, screenUnitsConverter: ScreenUnitsConverter): DefaultLayoutProvider {
        return DefaultLayoutProvider(context, screenUnitsConverter)
    }
}