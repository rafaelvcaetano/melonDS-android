package me.magnum.melonds.di

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import androidx.preference.PreferenceManager
import androidx.room.Room
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.squareup.picasso.Picasso
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ApplicationComponent
import dagger.hilt.android.qualifiers.ApplicationContext
import io.reactivex.android.schedulers.AndroidSchedulers
import me.magnum.melonds.common.Schedulers
import me.magnum.melonds.common.uridelegates.CompositeUriHandler
import me.magnum.melonds.common.uridelegates.UriHandler
import me.magnum.melonds.database.MelonDatabase
import me.magnum.melonds.database.migrations.Migration1to2
import me.magnum.melonds.utils.UriTypeHierarchyAdapter
import javax.inject.Singleton

@Module
@InstallIn(ApplicationComponent::class)
object AppModule {
    @Provides
    fun provideContext(@ApplicationContext context: Context): Context = context

    @Provides
    fun provideSharedPreferences(@ApplicationContext context: Context): SharedPreferences {
        return PreferenceManager.getDefaultSharedPreferences(context)
    }

    @Provides
    @Singleton
    fun provideGson(): Gson {
        return GsonBuilder()
                .registerTypeHierarchyAdapter(Uri::class.java, UriTypeHierarchyAdapter())
                .create()
    }

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): MelonDatabase {
        return Room.databaseBuilder(context, MelonDatabase::class.java, "melon-database")
                .addMigrations(Migration1to2())
                .build()
    }

    @Provides
    @Singleton
    fun provideUriHandler(@ApplicationContext context: Context): UriHandler {
        return CompositeUriHandler(context)
    }

    @Provides
    @Singleton
    fun providePicasso(@ApplicationContext context: Context): Picasso {
        return Picasso.Builder(context).build()
    }

    @Provides
    @Singleton
    fun provideSchedulers(): Schedulers {
        return Schedulers(io.reactivex.schedulers.Schedulers.io(), AndroidSchedulers.mainThread())
    }
}