package me.magnum.melonds.ui.input;

import me.magnum.melonds.model.Input;
import me.magnum.melonds.model.Point;

public abstract class FrontendInputHandler implements IInputListener {
    @Override
    public void onKeyPress(Input key) {
        switch (key) {
            case PAUSE:
                onPausePressed();
                break;
            case FAST_FORWARD:
                onFastForwardPressed();
                break;
        }
    }

    @Override
    public void onKeyReleased(Input key) {

    }

    @Override
    public void onTouch(Point point) {

    }

    public abstract void onPausePressed();
    public abstract void onFastForwardPressed();
}
