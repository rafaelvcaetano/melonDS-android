package me.magnum.melonds.ui.layouts

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import dagger.hilt.android.AndroidEntryPoint
import me.magnum.melonds.R
import me.magnum.melonds.databinding.ActivityLayoutsBinding
import me.magnum.melonds.domain.model.LayoutConfiguration
import me.magnum.melonds.ui.layouts.fragments.LayoutSelectorFragment

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
            setOnLayoutSelectedListener {
                onLayoutSelected(it)
            }
        }

        supportFragmentManager.beginTransaction()
                .replace(R.id.frame_layout_list, fragment)
                .commit()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            android.R.id.home -> {
                setResult(Activity.RESULT_CANCELED)
                finish()
            }
            else -> return super.onOptionsItemSelected(item)
        }
        return true
    }

    private fun onLayoutSelected(layout: LayoutConfiguration) {
        val intent = Intent().apply {
            putExtra(KEY_SELECTED_LAYOUT_ID, layout.id?.toString())
        }
        setResult(Activity.RESULT_OK, intent)
        finish()
    }
}