package me.magnum.melonds.utils;

import android.view.View;
import android.view.ViewGroup;

public final class UIUtils {
    private UIUtils() {
    }

    public static void setViewEnabled(View view, boolean enabled) {
        view.setEnabled(enabled);
        if (view instanceof ViewGroup) {
            ViewGroup viewGroup = (ViewGroup) view;
            for (int i = 0; i < viewGroup.getChildCount(); i++) {
                View child = viewGroup.getChildAt(i);
                setViewEnabled(child, enabled);
            }
        }
    }
}
