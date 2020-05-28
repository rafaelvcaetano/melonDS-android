package me.magnum.melonds.model;

import androidx.annotation.NonNull;

public class InputConfig {
    public static final int KEY_NOT_SET = -1;

    private Input input;
    private int key;

    public InputConfig(Input input, int key) {
        this.input = input;
        this.key = key;
    }

    public InputConfig(Input input) {
        this(input, KEY_NOT_SET);
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
        return key != KEY_NOT_SET;
    }

    @NonNull
    @Override
    public InputConfig clone() {
        return new InputConfig(this.input, this.key);
    }
}
