package me.magnum.melonds.ui.settings.fragments

import android.os.Bundle
import androidx.preference.PreferenceFragmentCompat
import me.magnum.melonds.R
import me.magnum.melonds.ui.settings.PreferenceFragmentTitleProvider

class VideoPreferencesFragment : PreferenceFragmentCompat(), PreferenceFragmentTitleProvider {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.pref_video, rootKey)
    }

    override fun getTitle(): String {
        return getString(R.string.category_video)
    }
}