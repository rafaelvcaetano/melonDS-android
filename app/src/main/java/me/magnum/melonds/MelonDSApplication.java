package me.magnum.melonds;

import android.app.Application;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;
import android.content.Context;
import androidx.annotation.NonNull;
import androidx.preference.PreferenceManager;
import com.squareup.moshi.Moshi;
import io.reactivex.disposables.Disposable;
import io.reactivex.functions.Consumer;
import me.magnum.melonds.impl.FileSystemRomsRepository;
import me.magnum.melonds.impl.SharedPreferencesSettingsRepository;
import me.magnum.melonds.repositories.RomsRepository;
import me.magnum.melonds.repositories.SettingsRepository;
import me.magnum.melonds.ui.Theme;
import me.magnum.melonds.ui.inputsetup.InputSetupViewModel;
import me.magnum.melonds.ui.romlist.RomListViewModel;

public class MelonDSApplication extends Application {
    private Disposable themeObserverDisposable;

    @Override
    public void onCreate() {
        super.onCreate();
        initializeServiceLocator();
        applyTheme();
    }

    private void initializeServiceLocator() {
        ServiceLocator.bindSingleton(new Moshi.Builder().build());
        ServiceLocator.bindSingleton(Context.class, getApplicationContext());
        ServiceLocator.bindSingleton(SettingsRepository.class, new SharedPreferencesSettingsRepository(this, PreferenceManager.getDefaultSharedPreferences(this), ServiceLocator.get(Moshi.class)));
        ServiceLocator.bindSingleton(RomsRepository.class, new FileSystemRomsRepository(ServiceLocator.get(Context.class), ServiceLocator.get(Moshi.class), ServiceLocator.get(SettingsRepository.class)));

        ServiceLocator.bindSingleton(ViewModelProvider.Factory.class, new ViewModelProvider.Factory() {
            @NonNull
            @Override
            public <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
                if (modelClass == RomListViewModel.class)
                    return (T) new RomListViewModel(ServiceLocator.get(RomsRepository.class));
                if (modelClass == InputSetupViewModel.class)
                    return (T) new InputSetupViewModel(ServiceLocator.get(SettingsRepository.class));

                throw new RuntimeException("ViewModel of type " + modelClass.getName() + " is not supported");
            }
        });
    }

    private void applyTheme() {
        SettingsRepository settingsRepository = ServiceLocator.get(SettingsRepository.class);

        Theme theme = settingsRepository.getTheme();
        AppCompatDelegate.setDefaultNightMode(theme.getNightMode());

        this.themeObserverDisposable = settingsRepository.observeTheme().subscribe(new Consumer<Theme>() {
            @Override
            public void accept(Theme theme) {
                AppCompatDelegate.setDefaultNightMode(theme.getNightMode());
            }
        });
    }

    @Override
    public void onTerminate() {
        super.onTerminate();
        if (themeObserverDisposable != null)
            themeObserverDisposable.dispose();
    }
}
