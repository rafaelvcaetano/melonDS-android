package me.magnum.melonds.di

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import androidx.preference.PreferenceManager
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ApplicationComponent
import dagger.hilt.android.qualifiers.ApplicationContext
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
}