package me.magnum.melonds.extensions

import androidx.preference.Preference
import me.magnum.melonds.utils.CompositeOnPreferenceChangeListener

fun Preference.addOnPreferenceChangeListener(listener: Preference.OnPreferenceChangeListener) {
    val currentListener = onPreferenceChangeListener
    if (currentListener is CompositeOnPreferenceChangeListener) {
        currentListener.addOnPreferenceChangeListener(listener)
    } else {
        onPreferenceChangeListener = CompositeOnPreferenceChangeListener().apply {
            if (currentListener != null) {
                addOnPreferenceChangeListener(currentListener)
            }
            addOnPreferenceChangeListener(listener)
        }
    }
}