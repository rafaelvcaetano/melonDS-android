package me.magnum.melonds.ui.settings

import android.os.Bundle
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class SettingsActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setupActionBar()

        supportFragmentManager.addOnBackStackChangedListener {
            val fragment = supportFragmentManager.fragments.lastOrNull()
            if (fragment is BasePreferencesFragment) {
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
}