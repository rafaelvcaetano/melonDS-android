package me.magnum.melonds

import android.app.Application
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.appcompat.app.AppCompatDelegate
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import io.reactivex.disposables.Disposable
import me.magnum.melonds.domain.repositories.SettingsRepository
import me.magnum.melonds.migrations.Migrator
import javax.inject.Inject

@HiltAndroidApp
class MelonDSApplication : Application(), Configuration.Provider {
    companion object {
        const val NOTIFICATION_CHANNEL_ID_BACKGROUND_TASKS = "channel_cheat_importing"
    }

    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var settingsRepository: SettingsRepository
    @Inject lateinit var migrator: Migrator

    private var themeObserverDisposable: Disposable? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
        applyTheme()
        performMigrations()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return
        }

        val defaultChannel = NotificationChannel(NOTIFICATION_CHANNEL_ID_BACKGROUND_TASKS, getString(R.string.notification_channel_background_tasks), NotificationManager.IMPORTANCE_LOW)
        defaultChannel.lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(defaultChannel)
    }

    private fun applyTheme() {
        val theme = settingsRepository.getTheme()

        AppCompatDelegate.setDefaultNightMode(theme.nightMode)
        themeObserverDisposable = settingsRepository.observeTheme().subscribe { AppCompatDelegate.setDefaultNightMode(it.nightMode) }
    }

    private fun performMigrations() {
        migrator.performMigrations()
    }

    override fun getWorkManagerConfiguration(): Configuration {
        return Configuration.Builder()
                .setWorkerFactory(workerFactory)
                .build()
    }

    override fun onTerminate() {
        super.onTerminate()
        themeObserverDisposable?.dispose()
    }
}