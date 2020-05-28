package me.magnum.melonds.repositories;

import io.reactivex.Observable;
import me.magnum.melonds.model.ControllerConfiguration;
import me.magnum.melonds.model.VideoFiltering;
import me.magnum.melonds.ui.Theme;

public interface SettingsRepository {
    Theme getTheme();

    String[] getRomSearchDirectories();

    String getBiosDirectory();
    boolean showBootScreen();

    VideoFiltering getVideoFiltering();

    boolean saveNextToRomFile();
    String getSaveFileDirectory();

    ControllerConfiguration getControllerConfiguration();
    int getSoftInputOpacity();

    Observable<Theme> observeTheme();
    Observable<String[]> observeRomSearchDirectories();

    void setControllerConfiguration(ControllerConfiguration controllerConfiguration);
}
