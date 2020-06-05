package me.magnum.melonds

import android.app.Application
import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.preference.PreferenceManager
import com.google.gson.Gson
import io.reactivex.disposables.Disposable
import me.magnum.melonds.impl.FileSystemRomsRepository
import me.magnum.melonds.impl.SharedPreferencesSettingsRepository
import me.magnum.melonds.repositories.RomsRepository
import me.magnum.melonds.repositories.SettingsRepository
import me.magnum.melonds.ui.inputsetup.InputSetupViewModel
import me.magnum.melonds.ui.romlist.RomListViewModel

class MelonDSApplication : Application() {
    private var themeObserverDisposable: Disposable? = null

    override fun onCreate() {
        super.onCreate()
        initializeServiceLocator()
        applyTheme()
    }

    private fun initializeServiceLocator() {
        ServiceLocator.bindSingleton(Gson())
        ServiceLocator.bindSingleton(Context::class, applicationContext)
        ServiceLocator.bindSingleton(SettingsRepository::class, SharedPreferencesSettingsRepository(this, PreferenceManager.getDefaultSharedPreferences(this), ServiceLocator[Gson::class]))
        ServiceLocator.bindSingleton(RomsRepository::class, FileSystemRomsRepository(ServiceLocator[Context::class], ServiceLocator[Gson::class], ServiceLocator[SettingsRepository::class]))

        ServiceLocator.bindSingleton(ViewModelProvider.Factory::class, object : ViewModelProvider.Factory {
            override fun <T : ViewModel?> create(modelClass: Class<T>): T {
                if (modelClass == RomListViewModel::class.java)
                    return RomListViewModel(ServiceLocator[RomsRepository::class]) as T
                if (modelClass == InputSetupViewModel::class.java)
                    return InputSetupViewModel(ServiceLocator[SettingsRepository::class]) as T

                throw RuntimeException("ViewModel of type " + modelClass.name + " is not supported")
            }
        })
    }

    private fun applyTheme() {
        val settingsRepository = ServiceLocator[SettingsRepository::class]
        val theme = settingsRepository.getTheme()

        AppCompatDelegate.setDefaultNightMode(theme.nightMode)
        themeObserverDisposable = settingsRepository.observeTheme().subscribe { AppCompatDelegate.setDefaultNightMode(it.nightMode) }
    }

    override fun onTerminate() {
        super.onTerminate()
        themeObserverDisposable?.dispose()
    }
}