package me.magnum.melonds.ui;

import androidx.appcompat.app.AppCompatDelegate;

public enum Theme {
    LIGHT(AppCompatDelegate.MODE_NIGHT_NO),
    DARK(AppCompatDelegate.MODE_NIGHT_YES),
    SYSTEM(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);

    private int nightMode;

    Theme(int nightMode) {
        this.nightMode = nightMode;
    }

    public int getNightMode() {
        return nightMode;
    }
}
