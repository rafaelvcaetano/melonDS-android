package me.magnum.melonds.di

import android.content.Context
import android.content.SharedPreferences
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ApplicationComponent
import dagger.hilt.android.qualifiers.ApplicationContext
import me.magnum.melonds.impl.FileSystemRomsRepository
import me.magnum.melonds.impl.RomIconProvider
import me.magnum.melonds.migrations.*

@Module
@InstallIn(ApplicationComponent::class)
object MigrationModule {
    @Provides
    fun provideMigration(@ApplicationContext context: Context, sharedPreferences: SharedPreferences, romIconProvider: RomIconProvider, romsRepository: FileSystemRomsRepository): Migrator {
        return Migrator(context, sharedPreferences).apply {
            registerMigration(Migration6to7(sharedPreferences))
            registerMigration(Migration7to8(context))
            registerMigration(Migration14to15(romIconProvider))
            registerMigration(Migration16to17(romsRepository))
        }
    }
}