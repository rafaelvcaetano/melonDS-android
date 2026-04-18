package me.magnum.melonds.migrations

import android.content.Context
import androidx.core.content.edit
import androidx.preference.PreferenceManager

class Migration34to35(private val context: Context) : Migration {

    override val from = 34
    override val to = 35

    override fun migrate() {
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
        sharedPreferences.edit {
            val showSoftInputPref = sharedPreferences.getBoolean("input_show_soft", true)
            remove("input_show_soft")

            val softInputBehaviourPreference = if (showSoftInputPref) {
                "hide_system_buttons_when_controller_connected"
            } else {
                "always_invisible"
            }
            putString("soft_input_behaviour", softInputBehaviourPreference)
        }
    }
}