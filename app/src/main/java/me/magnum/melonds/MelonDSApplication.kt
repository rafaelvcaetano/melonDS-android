package me.magnum.melonds

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.appcompat.app.AppCompatDelegate
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.preference.PreferenceManager
import com.google.gson.*
import io.reactivex.disposables.Disposable
import me.magnum.melonds.impl.FileSystemRomsRepository
import me.magnum.melonds.impl.SharedPreferencesSettingsRepository
import me.magnum.melonds.domain.repositories.RomsRepository
import me.magnum.melonds.domain.repositories.SettingsRepository
import me.magnum.melonds.ui.emulator.EmulatorViewModel
import me.magnum.melonds.ui.inputsetup.InputSetupViewModel
import me.magnum.melonds.ui.romlist.RomListViewModel
import me.magnum.melonds.utils.RomIconProvider
import java.lang.reflect.Type

class MelonDSApplication : Application() {
    private var themeObserverDisposable: Disposable? = null

    override fun onCreate() {
        super.onCreate()
        initializeServiceLocator()
        applyTheme()
    }

    private fun initializeServiceLocator() {
        val gson = GsonBuilder()
                .registerTypeAdapter(Uri::class.java, object : JsonSerializer<Uri?> {
                    override fun serialize(src: Uri?, typeOfSrc: Type?, context: JsonSerializationContext?): JsonElement {
                        return JsonPrimitive(src?.toString())
                    }
                })
                .registerTypeAdapter(Uri::class.java, object : JsonDeserializer<Uri?> {
                    override fun deserialize(json: JsonElement?, typeOfT: Type?, context: JsonDeserializationContext?): Uri? {
                        return json?.let { Uri.parse(it.asString) }
                    }
                })
                .create()
        ServiceLocator.bindSingleton(gson)
        ServiceLocator.bindSingleton(Context::class, applicationContext)
        ServiceLocator.bindSingleton(SettingsRepository::class, SharedPreferencesSettingsRepository(this, PreferenceManager.getDefaultSharedPreferences(this), ServiceLocator[Gson::class]))
        ServiceLocator.bindSingleton(RomsRepository::class, FileSystemRomsRepository(ServiceLocator[Context::class], ServiceLocator[Gson::class], ServiceLocator[SettingsRepository::class]))
        ServiceLocator.bindSingleton(RomIconProvider(this))

        ServiceLocator.bindSingleton(ViewModelProvider.Factory::class, object : ViewModelProvider.Factory {
            override fun <T : ViewModel?> create(modelClass: Class<T>): T {
                if (modelClass == RomListViewModel::class.java)
                    return RomListViewModel(ServiceLocator[Context::class], ServiceLocator[RomsRepository::class], ServiceLocator[SettingsRepository::class]) as T
                if (modelClass == InputSetupViewModel::class.java)
                    return InputSetupViewModel(ServiceLocator[SettingsRepository::class]) as T
                if (modelClass == EmulatorViewModel::class.java)
                    return EmulatorViewModel(ServiceLocator[Context::class], ServiceLocator[SettingsRepository::class], ServiceLocator[RomsRepository::class]) as T

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