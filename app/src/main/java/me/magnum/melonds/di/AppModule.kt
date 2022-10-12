package me.magnum.melonds.di

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.text.util.Linkify
import androidx.preference.PreferenceManager
import androidx.room.Room
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.squareup.picasso.Picasso
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.noties.markwon.Markwon
import io.noties.markwon.image.picasso.PicassoImagesPlugin
import io.noties.markwon.linkify.LinkifyPlugin
import io.reactivex.android.schedulers.AndroidSchedulers
import me.magnum.melonds.common.DirectoryAccessValidator
import me.magnum.melonds.common.Schedulers
import me.magnum.melonds.common.UriPermissionManager
import me.magnum.melonds.common.uridelegates.CompositeUriHandler
import me.magnum.melonds.common.uridelegates.UriHandler
import me.magnum.melonds.database.MelonDatabase
import me.magnum.melonds.database.migrations.Migration1to2
import me.magnum.melonds.utils.UriTypeHierarchyAdapter
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides
    fun provideContext(@ApplicationContext context: Context): Context = context

    @Provides
    @Singleton
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
    fun provideMarkwon(@ApplicationContext context: Context, picasso: Picasso): Markwon {
        return Markwon.builder(context)
            .usePlugin(PicassoImagesPlugin.create(picasso))
            .usePlugin(LinkifyPlugin.create(Linkify.WEB_URLS, true))
            .build()
    }

    @Provides
    @Singleton
    fun provideSchedulers(): Schedulers {
        return Schedulers(io.reactivex.schedulers.Schedulers.io(), AndroidSchedulers.mainThread())
    }

    @Provides
    @Singleton
    fun provideUriPermissionManager(@ApplicationContext context: Context): UriPermissionManager {
        return UriPermissionManager(context)
    }

    @Provides
    @Singleton
    fun providesDirectoryAccessValidator(@ApplicationContext context: Context): DirectoryAccessValidator {
        return DirectoryAccessValidator(context)
    }
}