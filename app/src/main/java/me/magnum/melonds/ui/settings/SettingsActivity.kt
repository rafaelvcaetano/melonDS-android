package me.magnum.melonds.ui.settings

import android.os.Bundle
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.commit
import androidx.fragment.app.replace
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import dagger.hilt.android.AndroidEntryPoint
import me.magnum.melonds.R
import me.magnum.melonds.databinding.ActivitySettingsBinding
import me.magnum.melonds.ui.settings.fragments.MainPreferencesFragment

@AndroidEntryPoint
class SettingsActivity : AppCompatActivity(), PreferenceFragmentCompat.OnPreferenceStartFragmentCallback {

    private lateinit var binding: ActivitySettingsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setupActionBar()

        supportFragmentManager.addOnBackStackChangedListener {
            updateTitle()
        }

        if (savedInstanceState == null) {
            supportFragmentManager.commit {
                replace<MainPreferencesFragment>(binding.settingsContainer.id)
            }
        } else {
            updateTitle()
        }
    }

    private fun setupActionBar() {
        val actionBar = supportActionBar
        actionBar?.setDisplayHomeAsUpEnabled(true)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        val id = item.itemId
        if (id == android.R.id.home) {
            if (!popBackStackIfNeeded()) {
                finish()
            }
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    private fun updateTitle() {
        val fragment = supportFragmentManager.fragments.lastOrNull()
        if (fragment is PreferenceFragmentTitleProvider) {
            supportActionBar?.title = fragment.getTitle()
        }
    }

    private fun popBackStackIfNeeded(): Boolean {
        if (supportFragmentManager.backStackEntryCount > 0) {
            supportFragmentManager.popBackStack()
            return true
        }
        return false
    }

    override fun onPreferenceStartFragment(caller: PreferenceFragmentCompat, pref: Preference): Boolean {
        val fragmentClassName = pref.fragment ?: return false

        val fragment = supportFragmentManager.fragmentFactory.instantiate(ClassLoader.getSystemClassLoader(), fragmentClassName).apply {
            arguments = pref.extras
        }

        supportFragmentManager.commit {
            setCustomAnimations(R.anim.fragment_translate_enter_push, R.anim.fragment_translate_exit_push, R.anim.fragment_translate_enter_pop, R.anim.fragment_translate_exit_pop)
            replace(binding.settingsContainer.id, fragment)
            addToBackStack(null)
        }
        return true
    }
}