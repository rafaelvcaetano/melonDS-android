package me.magnum.melonds.ui.settings

import android.os.Bundle
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import dagger.hilt.android.AndroidEntryPoint
import me.magnum.melonds.R

@AndroidEntryPoint
class SettingsActivity : AppCompatActivity(), PreferenceFragmentCompat.OnPreferenceStartFragmentCallback {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setupActionBar()

        supportFragmentManager.addOnBackStackChangedListener {
            val fragment = supportFragmentManager.fragments.lastOrNull()
            if (fragment is PreferenceFragmentTitleProvider) {
                supportActionBar?.title = fragment.getTitle()
            }
        }

        supportFragmentManager
                .beginTransaction()
                .replace(android.R.id.content, MainPreferencesFragment())
                .commit()
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

    private fun popBackStackIfNeeded(): Boolean {
        if (supportFragmentManager.backStackEntryCount > 0) {
            supportFragmentManager.popBackStack()
            return true
        }
        return false
    }

    override fun onPreferenceStartFragment(caller: PreferenceFragmentCompat?, pref: Preference?): Boolean {
        if (pref == null) {
            return false
        }

        val fragment = supportFragmentManager.fragmentFactory.instantiate(ClassLoader.getSystemClassLoader(), pref.fragment).apply {
            arguments = pref.extras
        }

        supportFragmentManager.beginTransaction()
                .setCustomAnimations(R.anim.fragment_translate_enter_push, R.anim.fragment_translate_exit_push, R.anim.fragment_translate_enter_pop, R.anim.fragment_translate_exit_pop)
                .replace(android.R.id.content, fragment)
                .addToBackStack(null)
                .commit()
        return true
    }
}