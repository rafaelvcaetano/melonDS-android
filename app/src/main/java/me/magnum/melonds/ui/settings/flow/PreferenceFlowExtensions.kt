package me.magnum.melonds.ui.settings.flow

import androidx.preference.Preference
import androidx.preference.SwitchPreference
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import me.magnum.melonds.extensions.addOnPreferenceChangeListener
import me.magnum.melonds.extensions.removeOnPreferenceChangeListener

fun SwitchPreference.observeAsFlow(): Flow<Boolean> = callbackFlow {
    channel.trySend(isChecked)
    val listener = Preference.OnPreferenceChangeListener { _, checked ->
        channel.trySend(checked as Boolean)
        true
    }
    addOnPreferenceChangeListener(listener)

    awaitClose { removeOnPreferenceChangeListener(listener) }
}