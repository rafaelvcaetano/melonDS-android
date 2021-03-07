package me.magnum.melonds.ui.layouts

import android.os.Bundle
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import dagger.hilt.android.AndroidEntryPoint
import me.magnum.melonds.R
import me.magnum.melonds.databinding.ActivityLayoutsBinding

@AndroidEntryPoint
class LayoutsActivity : AppCompatActivity() {
    private val viewModel: LayoutsViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val binding = ActivityLayoutsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val fragment = LayoutListFragment(viewModel.getSelectedLayoutId()).apply {
            setOnLayoutSelectedListener {
                viewModel.setSelectedLayout(it)
            }
        }

        supportFragmentManager.beginTransaction()
                .replace(R.id.frame_layout_list, fragment)
                .commit()
    }
}