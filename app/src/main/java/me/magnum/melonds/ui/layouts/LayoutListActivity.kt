package me.magnum.melonds.ui.layouts

import android.os.Bundle
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import dagger.hilt.android.AndroidEntryPoint
import me.magnum.melonds.R
import me.magnum.melonds.databinding.ActivityLayoutsBinding
import me.magnum.melonds.ui.layouts.fragments.LayoutListFragment

@AndroidEntryPoint
class LayoutListActivity : AppCompatActivity() {
    private val viewModel: LayoutsViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val binding = ActivityLayoutsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val fragment = LayoutListFragment().apply {
            setOnLayoutSelectedListener { id, _ ->
                viewModel.setSelectedLayoutId(id)
            }
        }

        supportFragmentManager.beginTransaction()
                .replace(R.id.frame_layout_list, fragment)
                .commit()
    }
}