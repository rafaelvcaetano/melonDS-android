package me.magnum.melonds.ui.settings

import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePadding
import androidx.fragment.app.commit
import androidx.fragment.app.commitNow
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import dagger.hilt.android.AndroidEntryPoint
import me.magnum.melonds.R
import me.magnum.melonds.databinding.ActivitySettingsBinding
import me.magnum.melonds.ui.settings.fragments.CustomFirmwarePreferencesFragment
import me.magnum.melonds.ui.settings.fragments.MainPreferencesFragment

@AndroidEntryPoint
class SettingsActivity : AppCompatActivity(), PreferenceFragmentCompat.OnPreferenceStartFragmentCallback {

    companion object {
        const val KEY_ENTRY_POINT = "entry_point"

        const val CUSTOM_FIRMWARE_ENTRY_POINT = "custom_firmware_entry_point"
    }

    private lateinit var binding: ActivitySettingsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.enableEdgeToEdge(window)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        var defaultContentInsetStartWithNavigation = -1
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, windowInsets ->
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            if (defaultContentInsetStartWithNavigation == -1) {
                defaultContentInsetStartWithNavigation = binding.toolbar.contentInsetStartWithNavigation
            }

            val startInset = if (binding.toolbar.layoutDirection == View.LAYOUT_DIRECTION_LTR) insets.left else insets.right
            binding.toolbar.contentInsetStartWithNavigation = defaultContentInsetStartWithNavigation + startInset
            binding.toolbar.updatePadding(
                left = insets.left,
                right = insets.right,
            )
            binding.viewStatusBarBackground.updateLayoutParams {
                height = insets.top
            }
            binding.settingsContainer.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                leftMargin = insets.left
                rightMargin = insets.right
            }

            // Leave bottom insets to be consumed by the content fragments
            windowInsets.inset(insets.left, insets.top, insets.right, 0)
        }

        supportFragmentManager.addOnBackStackChangedListener {
            updateTitle()
        }

        if (savedInstanceState == null) {
            val entryPoint = when (intent.extras?.getString(KEY_ENTRY_POINT)) {
                CUSTOM_FIRMWARE_ENTRY_POINT -> CustomFirmwarePreferencesFragment::class
                else -> MainPreferencesFragment::class
            }

            supportFragmentManager.commitNow {
                replace(binding.settingsContainer.id, entryPoint.java, null)
            }
        }
        updateTitle()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        val id = item.itemId
        if (id == android.R.id.home) {
            if (!supportFragmentManager.popBackStackImmediate()) {
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