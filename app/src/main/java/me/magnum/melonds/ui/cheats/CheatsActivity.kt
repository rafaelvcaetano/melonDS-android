package me.magnum.melonds.ui.cheats

import android.os.Bundle
import android.view.MenuItem
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.lifecycle.Observer
import dagger.hilt.android.AndroidEntryPoint
import me.magnum.melonds.R
import me.magnum.melonds.databinding.ActivityCheatsBinding
import me.magnum.melonds.parcelables.RomInfoParcelable

@AndroidEntryPoint
class CheatsActivity : AppCompatActivity() {
    companion object {
        const val KEY_ROM_INFO = "key_rom_info"
    }

    private val viewModel: CheatsViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val binding = ActivityCheatsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        val romInfoParcelable = intent.getParcelableExtra<RomInfoParcelable>(KEY_ROM_INFO) ?: throw NullPointerException("KEY_ROM_INFO argument is required")

        val cheatsFragment = CheatsFragment()
        cheatsFragment.setOnContentTitleChangedListener {
            if (it == null) {
                supportActionBar?.title = getString(R.string.cheats)
            } else {
                supportActionBar?.title = it
            }
        }

        viewModel.getRomCheats(romInfoParcelable.toRomInfo()).observe(this, Observer {
            binding.progressBarCheats.isGone = true

            if (it.isEmpty()) {
                binding.textCheatsNotFound.isVisible = true
            } else {
                supportFragmentManager.beginTransaction()
                        .replace(binding.cheatsRoot.id, cheatsFragment)
                        .commit()
            }
        })

        viewModel.committingCheatsChangesStatus().observe(this, Observer {
            binding.viewBlock.isGone = !it
        })

        viewModel.onCheatChangesCommitted().observe(this, Observer {
            finish()
        })
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                if (!popChildBackStackIfNeeded()) {
                    commitCheatChangesAndFinish()
                }
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onBackPressed() {
        if (!popChildBackStackIfNeeded()) {
            commitCheatChangesAndFinish()
        }
    }

    private fun popChildBackStackIfNeeded(): Boolean {
        supportFragmentManager.fragments.forEach { fragment ->
            if (fragment.isVisible) {
                if (fragment.childFragmentManager.backStackEntryCount > 1) {
                    fragment.childFragmentManager.popBackStack()
                    return true
                }
            }
        }
        return false
    }

    private fun commitCheatChangesAndFinish() {
        // If changes are already being committed, do nothing
        if (viewModel.committingCheatsChangesStatus().value == true) {
            return
        }

        viewModel.commitCheatChanges().observe(this, Observer {
            if (!it) {
                Toast.makeText(this, R.string.failed_save_cheat_changes, Toast.LENGTH_LONG).show()
            }
        })
    }
}