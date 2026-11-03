package me.magnum.melonds

import android.app.Application
import android.content.pm.ApplicationInfo
import android.os.StrictMode
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationManagerCompat
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import me.magnum.melonds.common.UriFileHandler
import me.magnum.melonds.common.uridelegates.UriHandler
import me.magnum.melonds.domain.repositories.SettingsRepository
import me.magnum.melonds.impl.SettingsRestoreRecovery
import me.magnum.melonds.migrations.Migrator
import javax.inject.Inject

@HiltAndroidApp
class MelonDSApplication : Application(), Configuration.Provider {
    companion object {
        const val NOTIFICATION_CHANNEL_ID_BACKGROUND_TASKS = "channel_cheat_importing"

        init {
            System.loadLibrary("melonDS-android-frontend")
        }
    }

    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var settingsRepository: SettingsRepository
    @Inject lateinit var migrator: Migrator
    @Inject lateinit var uriHandler: UriHandler

    override fun onCreate() {
        super.onCreate()
        SettingsRestoreRecovery.recoverAll(filesDir)
        enableStrictModeOnDebuggableBuilds()
        createNotificationChannels()
        applyTheme()
        performMigrations()
        MelonDSAndroidInterface.setup(UriFileHandler(this, uriHandler))
    }

    /**
     * Development builds only: log (never crash on) disk/network work on the main thread and leaked closeables,
     * so that ANR-prone code paths show up in logcat as "StrictMode policy violation".
     */
    private fun enableStrictModeOnDebuggableBuilds() {
        if ((applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) == 0) return
        StrictMode.setThreadPolicy(
            StrictMode.ThreadPolicy.Builder()
                .detectDiskReads()
                .detectDiskWrites()
                .detectNetwork()
                .detectCustomSlowCalls()
                .penaltyLog()
                .build()
        )
        StrictMode.setVmPolicy(
            StrictMode.VmPolicy.Builder()
                .detectLeakedClosableObjects()
                .detectLeakedSqlLiteObjects()
                .penaltyLog()
                .build()
        )
    }

    private fun createNotificationChannels() {
        val defaultChannel = NotificationChannelCompat.Builder(NOTIFICATION_CHANNEL_ID_BACKGROUND_TASKS, NotificationManagerCompat.IMPORTANCE_LOW)
            .setName(getString(R.string.notification_channel_background_tasks))
            .build()

        val notificationManager = NotificationManagerCompat.from(this)
        notificationManager.createNotificationChannel(defaultChannel)
    }

    @OptIn(DelicateCoroutinesApi::class)
    private fun applyTheme() {
        GlobalScope.launch(Dispatchers.Main) {
            settingsRepository.observeTheme().collect {
                AppCompatDelegate.setDefaultNightMode(it.nightMode)
            }
        }
    }

    private fun performMigrations() {
        migrator.performMigrations()
    }

    override fun onTerminate() {
        super.onTerminate()
        MelonDSAndroidInterface.cleanup()
    }

    override val workManagerConfiguration: Configuration get() {
        return Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()
    }
}
