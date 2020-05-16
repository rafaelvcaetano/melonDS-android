package me.magnum.melonds;

import android.view.KeyEvent;
import android.view.MotionEvent;

public interface INativeInputListener {
	boolean onMotionEvent(MotionEvent motionEvent);
	boolean onKeyEvent(KeyEvent keyEvent);
}
