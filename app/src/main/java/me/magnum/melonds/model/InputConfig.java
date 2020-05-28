package me.magnum.melonds.model;

import androidx.annotation.NonNull;

public class InputConfig {
    private Input input;
    private int key;

    public InputConfig(Input input, int key) {
        this.input = input;
        this.key = key;
    }

    public InputConfig(Input input) {
        this(input, -1);
    }

    public Input getInput() {
        return input;
    }

    public int getKey() {
        return key;
    }

    public void setKey(int key) {
        this.key = key;
    }

    public boolean hasKeyAssigned() {
        return key != -1;
    }

    @NonNull
    @Override
    public InputConfig clone() {
        return new InputConfig(this.input, this.key);
    }
}
