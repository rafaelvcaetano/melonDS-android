package me.magnum.melonds.di

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.text.util.Linkify
import androidx.preference.PreferenceManager
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.squareup.picasso.Picasso
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.noties.markwon.Markwon
import io.noties.markwon.image.picasso.PicassoImagesPlugin
import io.noties.markwon.linkify.LinkifyPlugin
import kotlinx.serialization.json.Json
import me.magnum.melonds.common.DirectoryAccessValidator
import me.magnum.melonds.common.PermissionHandler
import me.magnum.melonds.common.UriPermissionManager
import me.magnum.melonds.common.uridelegates.CompositeUriHandler
import me.magnum.melonds.common.uridelegates.UriHandler
import me.magnum.melonds.impl.system.AppForegroundStateObserver
import me.magnum.melonds.impl.system.AppForegroundStateTracker
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
    fun provideJson(): Json {
        return Json {
            ignoreUnknownKeys = true
            explicitNulls = false
        }
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
    fun provideUriPermissionManager(@ApplicationContext context: Context): UriPermissionManager {
        return UriPermissionManager(context)
    }

    @Provides
    @Singleton
    fun providesDirectoryAccessValidator(@ApplicationContext context: Context): DirectoryAccessValidator {
        return DirectoryAccessValidator(context)
    }

    @Provides
    @Singleton
    fun providePermissionHandler(@ApplicationContext context: Context): PermissionHandler {
        return PermissionHandler(context)
    }

    @Provides
    @Singleton
    fun provideAppForegroundStateTracker(): AppForegroundStateTracker {
        return AppForegroundStateTracker()
    }

    @Provides
    fun provideAppForegroundStateObserver(appForegroundStateTracker: AppForegroundStateTracker): AppForegroundStateObserver {
        return appForegroundStateTracker
    }
}