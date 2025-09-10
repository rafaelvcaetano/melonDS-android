package me.magnum.melonds.ui.layouts

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.commit
import dagger.hilt.android.AndroidEntryPoint
import me.magnum.melonds.R
import me.magnum.melonds.databinding.ActivityLayoutsBinding
import me.magnum.melonds.ui.layouts.fragments.ExternalLayoutSelectorFragment
import java.util.UUID

@AndroidEntryPoint
class ExternalLayoutSelectorActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val binding = ActivityLayoutsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        val fragment = ExternalLayoutSelectorFragment().apply {
            setOnLayoutSelectedListener { id, reason ->
                onLayoutSelected(id, reason)
            }
        }

        supportFragmentManager.commit {
            replace(R.id.frame_layout_list, fragment)
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                finish()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun onLayoutSelected(layoutId: UUID?, reason: BaseLayoutsFragment.LayoutSelectionReason) {
        val intent = Intent().apply {
            putExtra(LayoutSelectorActivity.KEY_SELECTED_LAYOUT_ID, layoutId?.toString())
        }
        setResult(Activity.RESULT_OK, intent)

        if (reason == BaseLayoutsFragment.LayoutSelectionReason.BY_USER) {
            finish()
        }
    }
}
