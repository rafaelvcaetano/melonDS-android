package me.magnum.melonds.ui.inputsetup;

import me.magnum.melonds.model.InputConfig;

public class StatefulInputConfig {
    private InputConfig inputConfig;
    private boolean isBeingConfigured;

    public StatefulInputConfig(InputConfig inputConfig) {
        this.inputConfig = inputConfig;
        this.isBeingConfigured = false;
    }

    public InputConfig getInputConfig() {
        return inputConfig;
    }

    public boolean isBeingConfigured() {
        return isBeingConfigured;
    }

    public void setIsBeingConfigured(boolean isBeingConfigured) {
        this.isBeingConfigured = isBeingConfigured;
    }
}
