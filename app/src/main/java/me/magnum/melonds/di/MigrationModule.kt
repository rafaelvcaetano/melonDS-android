package me.magnum.melonds.di

import android.content.Context
import android.content.SharedPreferences
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import me.magnum.melonds.common.DirectoryAccessValidator
import me.magnum.melonds.domain.repositories.RomsRepository
import me.magnum.melonds.domain.repositories.SettingsRepository
import me.magnum.melonds.impl.RomIconProvider
import me.magnum.melonds.migrations.*

@Module
@InstallIn(SingletonComponent::class)
object MigrationModule {

    @Provides
    fun provideMigration(
        @ApplicationContext context: Context,
        sharedPreferences: SharedPreferences,
        romIconProvider: RomIconProvider,
        romsRepository: RomsRepository,
        settingsRepository: SettingsRepository,
        directoryAccessValidator: DirectoryAccessValidator
    ): Migrator {

        return Migrator(context, sharedPreferences).apply {
            registerMigration(Migration6to7(sharedPreferences))
            registerMigration(Migration7to8(context))
            registerMigration(Migration14to15(romIconProvider))
            registerMigration(Migration16to17(romsRepository))
            registerMigration(Migration20to21(settingsRepository, romsRepository, directoryAccessValidator))
        }
    }
}