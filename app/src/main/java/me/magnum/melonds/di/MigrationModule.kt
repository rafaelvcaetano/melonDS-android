package me.magnum.melonds.di

import android.content.Context
import android.content.SharedPreferences
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import me.magnum.melonds.domain.repositories.RomsRepository
import me.magnum.melonds.impl.RomIconProvider
import me.magnum.melonds.migrations.*

@Module
@InstallIn(SingletonComponent::class)
object MigrationModule {
    @Provides
    fun provideMigration(@ApplicationContext context: Context, sharedPreferences: SharedPreferences, romIconProvider: RomIconProvider, romsRepository: RomsRepository): Migrator {
        return Migrator(context, sharedPreferences).apply {
            registerMigration(Migration6to7(sharedPreferences))
            registerMigration(Migration7to8(context))
            registerMigration(Migration14to15(romIconProvider))
            registerMigration(Migration16to17(romsRepository))
        }
    }
}