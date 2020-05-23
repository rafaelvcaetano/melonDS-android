package me.magnum.melonds.impl;

import android.content.SharedPreferences;
import android.os.Build;
import io.reactivex.Observable;
import io.reactivex.functions.Function;
import io.reactivex.subjects.PublishSubject;
import me.magnum.melonds.model.VideoFiltering;
import me.magnum.melonds.repositories.SettingsRepository;
import me.magnum.melonds.ui.Theme;

import java.util.HashMap;

public class SharedPreferencesSettingsRepository implements SettingsRepository, SharedPreferences.OnSharedPreferenceChangeListener {
    private SharedPreferences preferences;
    private HashMap<String, PublishSubject<Object>> preferenceObservers;

    public SharedPreferencesSettingsRepository(SharedPreferences preferences) {
        this.preferences = preferences;
        this.preferenceObservers = new HashMap<>();
        this.preferences.registerOnSharedPreferenceChangeListener(this);
        setDefaultThemeIfRequired();
    }

    private void setDefaultThemeIfRequired() {
        if (this.preferences.getString("theme", null) != null)
            return;

        String defaultTheme;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
            defaultTheme = "system";
        else
            defaultTheme = "light";

        this.preferences.edit().putString("theme", defaultTheme).apply();
    }

    @Override
    public Theme getTheme() {
        String themePreference = this.preferences.getString("theme", "light");
        return Theme.valueOf(themePreference.toUpperCase());
    }

    @Override
    public String[] getRomSearchDirectories() {
        String dirPreference = this.preferences.getString("rom_search_dirs", null);
        String[] dirs = getMultipleDirectoryFromPreference(dirPreference);
        if (dirs.length == 0)
            dirs = new String[] { "/sdcard" };

        return dirs;
    }

    @Override
    public String getBiosDirectory() {
        String dirPreference = this.preferences.getString("bios_dir", null);
        return getSingleDirectoryFromPreference(dirPreference);
    }

    @Override
    public boolean showBootScreen() {
        return this.preferences.getBoolean("show_bios", false);
    }

    @Override
    public VideoFiltering getVideoFiltering() {
        String filteringPreference = this.preferences.getString("video_filtering", "linear");
        return VideoFiltering.valueOf(filteringPreference.toUpperCase());
    }

    @Override
    public boolean saveNextToRomFile() {
        return this.preferences.getBoolean("use_rom_dir", true);
    }

    @Override
    public String getSaveFileDirectory() {
        String dirPreference = this.preferences.getString("sram_dir", null);
        return getSingleDirectoryFromPreference(dirPreference);
    }

    @Override
    public int getSoftInputOpacity() {
        return this.preferences.getInt("input_opacity", 50);
    }

    @Override
    public Observable<String[]> observeRomSearchDirectories() {
        return getOrCreatePreferenceObservable("rom_search_dirs", new Function<Object, String[]>() {
            @Override
            public String[] apply(Object o) {
                return getRomSearchDirectories();
            }
        });
    }

    @Override
    public Observable<Theme> observeTheme() {
        return getOrCreatePreferenceObservable("theme", new Function<Object, Theme>() {
            @Override
            public Theme apply(Object o) {
                return getTheme();
            }
        });
    }

    private <T> Observable<T> getOrCreatePreferenceObservable(String preference, Function<Object, T> mapper) {
        PublishSubject<Object> preferenceSubject = this.preferenceObservers.get(preference);
        if (preferenceSubject == null) {
            preferenceSubject = PublishSubject.create();
            this.preferenceObservers.put(preference, preferenceSubject);
        }

        return preferenceSubject.map(mapper);
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        PublishSubject<Object> subject = this.preferenceObservers.get(key);
        if (subject != null)
            subject.onNext(new Object());
    }

    /**
     * Retrieves a single directory from the given directory preference value. If multiple
     * directories are stored in the preference, only the first value is returned. If no directory
     * is stored in the preference value, {@code null} is returned. Directories are assumed to be
     * separated by a column (:).
     *
     * @param preferenceValue The directory preference value
     * @return The first directory found in the preference or {@code null} if there is none
     */
    private static String getSingleDirectoryFromPreference(String preferenceValue) {
        String[] parts = getMultipleDirectoryFromPreference(preferenceValue);
        if (parts.length > 0)
            return parts[0];

        return null;
    }

    /**
     * Retrieves all directory from the given directory preference value. If no directory is stored
     * in the preference value, an empty array is returned. Directories are assumed to be separated
     * by a column (:).
     *
     * @param preferenceValue The directory preference value
     * @return The directories found in the preference
     */
    private static String[] getMultipleDirectoryFromPreference(String preferenceValue) {
        if (preferenceValue == null)
            return new String[0];

        return preferenceValue.split(":");
    }
}
