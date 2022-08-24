package me.magnum.melonds.utils

import androidx.preference.Preference

class CompositeOnPreferenceChangeListener : Preference.OnPreferenceChangeListener {
    private val listeners = mutableListOf<Preference.OnPreferenceChangeListener>()

    override fun onPreferenceChange(preference: Preference, newValue: Any?): Boolean {
        val allListenersReturnedTrue = listeners.all { it.onPreferenceChange(preference, newValue) }
        return allListenersReturnedTrue
    }

    fun addOnPreferenceChangeListener(listener: Preference.OnPreferenceChangeListener) {
        listeners.add(listener)
    }
}