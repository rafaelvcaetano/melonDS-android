package me.magnum.melonds.di

import android.content.Context
import android.content.SharedPreferences
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ApplicationComponent
import dagger.hilt.android.qualifiers.ApplicationContext
import me.magnum.melonds.migrations.Migration6to7
import me.magnum.melonds.migrations.Migration7to8
import me.magnum.melonds.migrations.Migrator

@Module
@InstallIn(ApplicationComponent::class)
object MigrationModule {
    @Provides
    fun provideMigration(@ApplicationContext context: Context, sharedPreferences: SharedPreferences): Migrator {
        return Migrator(context, sharedPreferences).apply {
            registerMigration(Migration6to7(sharedPreferences))
            registerMigration(Migration7to8(context))
        }
    }
}