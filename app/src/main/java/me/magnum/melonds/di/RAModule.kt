package me.magnum.melonds.di

import android.content.SharedPreferences
import com.google.gson.Gson
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import me.magnum.melonds.common.retroachievements.AndroidRAAchievementSignatureProvider
import me.magnum.melonds.common.retroachievements.AndroidRAUserAuthStore
import me.magnum.rcheevosapi.RAAchievementSignatureProvider
import me.magnum.rcheevosapi.RAApi
import me.magnum.rcheevosapi.RAUserAuthStore
import okhttp3.OkHttpClient
import javax.inject.Named
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object RAModule {

    @Provides
    @Named("ra-api-client")
    fun provideRAApiOkHttpClient(): OkHttpClient {
        return OkHttpClient.Builder().build()
    }

    @Provides
    @Singleton
    fun provideRAUserAuthStore(sharedPreferences: SharedPreferences): RAUserAuthStore {
        return AndroidRAUserAuthStore(sharedPreferences)
    }

    @Provides
    @Singleton
    fun provideRAAchievementSignatureProvider(): RAAchievementSignatureProvider {
        return AndroidRAAchievementSignatureProvider()
    }

    @Provides
    @Singleton
    fun provideRAApi(@Named("ra-api-client") client: OkHttpClient, gson: Gson, userAuthStore: RAUserAuthStore, achievementSignatureProvider: RAAchievementSignatureProvider): RAApi {
        return RAApi(
            okHttpClient = client,
            gson = gson,
            userAuthStore = userAuthStore,
            achievementSignatureProvider = achievementSignatureProvider,
        )
    }
}