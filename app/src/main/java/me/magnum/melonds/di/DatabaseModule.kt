package me.magnum.melonds.di

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import me.magnum.melonds.database.MelonDatabase
import me.magnum.melonds.database.callback.CustomCheatCreationCallback
import me.magnum.melonds.database.daos.RAAchievementsDao
import me.magnum.melonds.database.migrations.Migration1to2
import me.magnum.melonds.database.migrations.Migration4to5
import me.magnum.melonds.impl.retroachievements.NoCacheRAAchievementsDao
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @IntoSet
    fun provideCustomCheatDatabaseCreationCallback(): RoomDatabase.Callback {
        return CustomCheatCreationCallback()
    }

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context, callbacks: Set<@JvmSuppressWildcards RoomDatabase.Callback>): MelonDatabase {
        return Room.databaseBuilder(context, MelonDatabase::class.java, "melon-database")
            .apply {
                callbacks.forEach {
                    addCallback(it)
                }
            }
            .addMigrations(Migration1to2(), Migration4to5())
            .build()
    }

    @Provides
    fun provideRAAchievementsDao(database: MelonDatabase): RAAchievementsDao {
        return NoCacheRAAchievementsDao(database.achievementsDao())
    }
}