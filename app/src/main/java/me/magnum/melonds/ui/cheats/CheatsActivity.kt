package me.magnum.melonds.ui.cheats

import android.os.Bundle
import android.view.MenuItem
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.fragment.app.commit
import dagger.hilt.android.AndroidEntryPoint
import me.magnum.melonds.R
import me.magnum.melonds.databinding.ActivityCheatsBinding

@AndroidEntryPoint
class CheatsActivity : AppCompatActivity() {
    companion object {
        const val KEY_ROM_INFO = "key_rom_info"

        private const val CHEATS_FRAGMENT_TAG = "cheats_fragment"
        private const val CHEATS_BACKSTACK = "cheats"
    }

    private lateinit var binding: ActivityCheatsBinding
    private val viewModel: CheatsViewModel by viewModels()

    private val backHandler = object : OnBackPressedCallback(true) {
        override fun handleOnBackPressed() {
            commitCheatChangesAndFinish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCheatsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        onBackPressedDispatcher.addCallback(backHandler)

        viewModel.getRomCheats().observe(this) {
            binding.progressBarCheats.isGone = true

            if (it.isEmpty()) {
                binding.textCheatsNotFound.isVisible = true
            } else if (savedInstanceState == null) {
                openCheatsFragment()
            }
        }

        viewModel.openEnabledCheatsEvent.observe(this) {
            openEnabledCheatsFragment()
        }

        viewModel.committingCheatsChangesStatus().observe(this) {
            binding.viewBlock.isGone = !it
        }

        viewModel.onCheatChangesCommitted().observe(this) {
            finish()
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                goBack()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun openCheatsFragment() {
        supportFragmentManager.commit {
            val fragment = CheatsFragment()

            add(binding.cheatsRoot.id, fragment, CHEATS_FRAGMENT_TAG)
            setPrimaryNavigationFragment(fragment)
        }
    }

    private fun openEnabledCheatsFragment() {
        supportFragmentManager.commit {
            val fragment = EnabledCheatsFragment()

            setCustomAnimations(R.anim.fragment_translate_enter_push, R.anim.fragment_translate_exit_push, R.anim.fragment_translate_enter_pop, R.anim.fragment_translate_exit_pop)
            replace(binding.cheatsRoot.id, fragment)
            addToBackStack(CHEATS_BACKSTACK)
            setPrimaryNavigationFragment(fragment)
        }
    }

    private fun goBack() {
        if (!supportFragmentManager.popBackStackImmediate()) {
            commitCheatChangesAndFinish()
        }
    }

    private fun commitCheatChangesAndFinish() {
        // If changes are already being committed, do nothing
        if (viewModel.committingCheatsChangesStatus().value == true) {
            return
        }

        viewModel.commitCheatChanges().observe(this) {
            if (!it) {
                Toast.makeText(this, R.string.failed_save_cheat_changes, Toast.LENGTH_LONG).show()
            }
        }
    }
}