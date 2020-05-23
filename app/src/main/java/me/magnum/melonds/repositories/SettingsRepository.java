package me.magnum.melonds.repositories;

import io.reactivex.Observable;
import me.magnum.melonds.model.VideoFiltering;
import me.magnum.melonds.ui.Theme;

public interface SettingsRepository {
    Theme getTheme();

    String getBiosDirectory();
    boolean showBootScreen();

    VideoFiltering getVideoFiltering();

    boolean saveNextToRomFile();
    String getSaveFileDirectory();

    int getSoftInputOpacity();

    Observable<Theme> observeTheme();
}
