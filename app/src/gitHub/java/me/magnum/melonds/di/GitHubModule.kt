package me.magnum.melonds.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import me.magnum.melonds.domain.services.UpdateInstallManager
import me.magnum.melonds.github.GitHubApi
import me.magnum.melonds.github.services.GitHubUpdateInstallManager
import okhttp3.MediaType.Companion.toMediaType
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object GitHubModule {
    @Provides
    @Singleton
    fun provideGitHubApi(json: Json): GitHubApi {
        val retrofit = Retrofit.Builder()
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .baseUrl("https://api.github.com")
            .build()

        return retrofit.create(GitHubApi::class.java)
    }

    @Provides
    @Singleton
    fun provideUpdateInstallManager(@ApplicationContext context: Context): UpdateInstallManager {
        return GitHubUpdateInstallManager(context)
    }
}