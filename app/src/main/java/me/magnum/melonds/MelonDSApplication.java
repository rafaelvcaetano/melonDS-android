package me.magnum.melonds;

import android.app.Application;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;
import android.content.Context;
import androidx.annotation.NonNull;
import androidx.preference.PreferenceManager;
import com.squareup.moshi.Moshi;
import me.magnum.melonds.impl.FileSystemRomsRepository;
import me.magnum.melonds.repositories.RomsRepository;
import me.magnum.melonds.ui.Theme;
import me.magnum.melonds.ui.romlist.RomListViewModel;

public class MelonDSApplication extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        initializeServiceLocator();
        applyTheme();
    }

    private void initializeServiceLocator() {
        ServiceLocator.bindSingleton(new Moshi.Builder().build());
        ServiceLocator.bindSingleton(Context.class, getApplicationContext());
        ServiceLocator.bindSingleton(RomsRepository.class, new FileSystemRomsRepository(ServiceLocator.get(Context.class), ServiceLocator.get(Moshi.class)));

        ServiceLocator.bindSingleton(ViewModelProvider.Factory.class, new ViewModelProvider.Factory() {
            @NonNull
            @Override
            public <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
                if (modelClass == RomListViewModel.class)
                    return (T) new RomListViewModel(ServiceLocator.get(RomsRepository.class));

                throw new RuntimeException("ViewModel of type " + modelClass.getName() + " is not supported");
            }
        });
    }

    private void applyTheme() {
        String themeValue = PreferenceManager.getDefaultSharedPreferences(this).getString("theme", "light");
        Theme theme = Theme.valueOf(themeValue.toUpperCase());
        AppCompatDelegate.setDefaultNightMode(theme.getNightMode());
    }
}
