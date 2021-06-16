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
import me.magnum.melonds.ui.layouts.fragments.LayoutSelectorFragment
import java.util.*

@AndroidEntryPoint
class LayoutSelectorActivity : AppCompatActivity() {
    companion object {
        const val KEY_SELECTED_LAYOUT_ID = "selected_layout_id"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val binding = ActivityLayoutsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        val fragment = LayoutSelectorFragment().apply {
            setOnLayoutSelectedListener { id, reason ->
                onLayoutSelected(id, reason)
            }
        }

        supportFragmentManager.commit {
            replace(R.id.frame_layout_list, fragment)
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            android.R.id.home -> {
                finish()
            }
            else -> return super.onOptionsItemSelected(item)
        }
        return true
    }

    private fun onLayoutSelected(layoutId: UUID?, reason: BaseLayoutsFragment.LayoutSelectionReason) {
        val intent = Intent().apply {
            putExtra(KEY_SELECTED_LAYOUT_ID, layoutId?.toString())
        }
        setResult(Activity.RESULT_OK, intent)

        // If the layout was selected by the user, close the activity immediately
        if (reason == BaseLayoutsFragment.LayoutSelectionReason.BY_USER) {
            finish()
        }
    }
}