package me.magnum.melonds.di

import android.content.Context
import com.google.gson.Gson
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import me.magnum.melonds.domain.services.UpdateInstallManager
import me.magnum.melonds.github.GitHubApi
import me.magnum.melonds.github.services.GitHubUpdateInstallManager
import retrofit2.Retrofit
import retrofit2.adapter.rxjava2.RxJava2CallAdapterFactory
import retrofit2.converter.gson.GsonConverterFactory
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object GitHubModule {
    @Provides
    @Singleton
    fun provideGitHubApi(gson: Gson): GitHubApi {
        val retrofit = Retrofit.Builder()
            .addCallAdapterFactory(RxJava2CallAdapterFactory.create())
            .addConverterFactory(GsonConverterFactory.create(gson))
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