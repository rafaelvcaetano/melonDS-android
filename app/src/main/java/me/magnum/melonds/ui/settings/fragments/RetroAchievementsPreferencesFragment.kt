package me.magnum.melonds.ui.settings.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SwitchPreference
import kotlinx.coroutines.launch
import me.magnum.melonds.R
import me.magnum.melonds.databinding.DialogRetroachievementsLoginBinding
import me.magnum.melonds.extensions.addOnPreferenceChangeListener
import me.magnum.melonds.ui.common.LoadingDialog
import me.magnum.melonds.ui.settings.PreferenceFragmentTitleProvider
import me.magnum.melonds.ui.settings.RetroAchievementsSettingsViewModel
import me.magnum.melonds.ui.settings.model.RetroAchievementsAccountState

class RetroAchievementsPreferencesFragment : PreferenceFragmentCompat(), PreferenceFragmentTitleProvider {

    private val viewModel by activityViewModels<RetroAchievementsSettingsViewModel>()

    private var loginProgressDialog: LoadingDialog? = null

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.pref_retroachievements, rootKey)

        val accountPreference = findPreference<Preference>("ra_login")!!
        val hardcoreModePreference = findPreference<SwitchPreference>("ra_hardcore_enabled")!!
        val richPresencePreference = findPreference<SwitchPreference>("ra_rich_presence")!!

        hardcoreModePreference.addOnPreferenceChangeListener { _, newValue ->
            val isEnabled = newValue as Boolean

            // Rich preference must be on when hardcore mode is enabled. As such, when hardcore is enabled, disable the preference and force it to be checked
            richPresencePreference.isEnabled = !isEnabled
            if (isEnabled) {
                richPresencePreference.isChecked = true
            }
            true
        }

        accountPreference.setOnPreferenceClickListener {
            val accountState = viewModel.accountState.value
            when (accountState) {
                is RetroAchievementsAccountState.LoggedIn -> showLogoutConfirmationDialog()
                RetroAchievementsAccountState.LoggedOut -> showLoginDialog()
                RetroAchievementsAccountState.Unknown -> {
                    // Do nothing until a proper state is known
                }
            }
            true
        }

        lifecycleScope.launch {
            lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.accountState.collect {
                    when (it) {
                        is RetroAchievementsAccountState.LoggedIn -> {
                            accountPreference.title = getString(R.string.retroachievements_logout)
                            accountPreference.summary = getString(R.string.retroachievements_login_status, it.accountName)
                            accountPreference.notifyDependencyChange(false)
                        }
                        RetroAchievementsAccountState.LoggedOut -> {
                            accountPreference.title = getString(R.string.login_with_retro_achievements)
                            accountPreference.summary = getString(R.string.retroachievements_login_summary)
                            accountPreference.notifyDependencyChange(true)
                        }
                        RetroAchievementsAccountState.Unknown -> {
                            accountPreference.title = getString(R.string.ellipsis)
                            accountPreference.summary = getString(R.string.ellipsis)
                            accountPreference.notifyDependencyChange(true)
                        }
                    }
                }
            }
        }

        lifecycleScope.launch {
            lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.loggingIn.collect { loggingIn ->
                    loginProgressDialog = if (loggingIn) {
                        LoadingDialog(requireContext()).apply {
                            show()
                        }
                    } else {
                        loginProgressDialog?.dismiss()
                        null
                    }
                }
            }
        }

        lifecycleScope.launch {
            lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                viewModel.loginErrorEvent.collect {
                    Toast.makeText(requireContext(), R.string.retro_achievements_login_error_short, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun showLoginDialog() {
        val binding = DialogRetroachievementsLoginBinding.inflate(LayoutInflater.from(context))
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.login_with_retro_achievements)
            .setView(binding.root)
            .setPositiveButton(R.string.login) { dialog, _ ->
                viewModel.login(
                    binding.textUsername.text?.toString() ?: "",
                    binding.textPassword.text?.toString() ?: "",
                )
                dialog.dismiss()
            }
            .setNegativeButton(R.string.cancel) { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }

    private fun showLogoutConfirmationDialog() {
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.retroachievements_logout)
            .setMessage(R.string.retroachievements_logout_confirmation)
            .setPositiveButton(R.string.retroachievements_logout) { dialog, _ ->
                viewModel.logoutFromRetroAchievements()
                dialog.dismiss()
            }
            .setNegativeButton(R.string.cancel) { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }

    override fun getTitle() = getString(R.string.retroachievements)
}