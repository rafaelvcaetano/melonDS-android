package me.magnum.melonds.ui.layouts

import android.os.Bundle
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.commit
import dagger.hilt.android.AndroidEntryPoint
import me.magnum.melonds.R
import me.magnum.melonds.databinding.ActivityLayoutsBinding
import me.magnum.melonds.ui.layouts.fragments.ExternalLayoutListFragment

@AndroidEntryPoint
class ExternalLayoutListActivity : AppCompatActivity() {
    private val viewModel: ExternalLayoutsViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val binding = ActivityLayoutsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val fragment = ExternalLayoutListFragment().apply {
            setOnLayoutSelectedListener { id, _ ->
                viewModel.setSelectedLayoutId(id)
            }
        }

        supportFragmentManager.commit {
            replace(R.id.frame_layout_list, fragment)
        }
    }
}
