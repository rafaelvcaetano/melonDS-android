package me.magnum.melonds.components;

import android.content.Context;
import android.support.v7.preference.Preference;
import android.util.AttributeSet;

public class ControllerInputPreference extends Preference {
	public ControllerInputPreference(Context context, AttributeSet attrs, int defStyleAttr) {
		super(context, attrs, defStyleAttr);
		this.setWidgetLayoutResource(defStyleAttr);
	}

	public ControllerInputPreference(Context context, AttributeSet attrs) {
		super(context, attrs);
	}
}
