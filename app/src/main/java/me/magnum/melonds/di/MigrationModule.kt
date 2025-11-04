package me.magnum.melonds.di

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import me.magnum.melonds.common.DirectoryAccessValidator
import me.magnum.melonds.common.uridelegates.UriHandler
import me.magnum.melonds.domain.repositories.RomsRepository
import me.magnum.melonds.domain.repositories.SettingsRepository
import me.magnum.melonds.impl.RomIconProvider
import me.magnum.melonds.migrations.*
import me.magnum.melonds.migrations.helper.GenericJsonArrayMigrationHelper

@Module
@InstallIn(SingletonComponent::class)
object MigrationModule {

    @Provides
    fun provideJsonArrayMigrationHelper(@ApplicationContext context: Context, gson: Gson): GenericJsonArrayMigrationHelper {
        return GenericJsonArrayMigrationHelper(context, gson)
    }

    @Provides
    fun provideMigration(
        @ApplicationContext context: Context,
        sharedPreferences: SharedPreferences,
        romIconProvider: RomIconProvider,
        romsRepository: RomsRepository,
        settingsRepository: SettingsRepository,
        directoryAccessValidator: DirectoryAccessValidator,
        uriHandler: UriHandler,
        gson: Gson,
        json: Json,
        genericJsonArrayMigrationHelper: GenericJsonArrayMigrationHelper,
    ): Migrator {

        return Migrator(context, sharedPreferences).apply {
            registerMigration(Migration6to7(sharedPreferences))
            registerMigration(Migration7to8(context))
            registerMigration(Migration14to15(romIconProvider))
            registerMigration(Migration16to17(romsRepository))
            registerMigration(Migration20to21(settingsRepository, romsRepository, directoryAccessValidator))
            registerMigration(Migration21to22(context, gson, uriHandler))
            registerMigration(Migration24to25(genericJsonArrayMigrationHelper, context))
            registerMigration(Migration25to26(genericJsonArrayMigrationHelper))
            registerMigration(Migration30to31(genericJsonArrayMigrationHelper))
            registerMigration(Migration31to32(context, genericJsonArrayMigrationHelper))
            registerMigration(Migration33to34(context, json))
            registerMigration(Migration34to35(context))
        }
    }
}