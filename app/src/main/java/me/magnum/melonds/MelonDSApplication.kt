package me.magnum.melonds

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import dagger.hilt.android.HiltAndroidApp
import io.reactivex.disposables.Disposable
import me.magnum.melonds.domain.repositories.SettingsRepository
import me.magnum.melonds.migrations.Migrator
import javax.inject.Inject

@HiltAndroidApp
class MelonDSApplication : Application() {
    @Inject lateinit var settingsRepository: SettingsRepository
    @Inject lateinit var migrator: Migrator

    private var themeObserverDisposable: Disposable? = null

    override fun onCreate() {
        super.onCreate()
        applyTheme()
        performMigrations()
    }

    private fun applyTheme() {
        val theme = settingsRepository.getTheme()

        AppCompatDelegate.setDefaultNightMode(theme.nightMode)
        themeObserverDisposable = settingsRepository.observeTheme().subscribe { AppCompatDelegate.setDefaultNightMode(it.nightMode) }
    }

    private fun performMigrations() {
        migrator.performMigrations()
    }

    override fun onTerminate() {
        super.onTerminate()
        themeObserverDisposable?.dispose()
    }
}