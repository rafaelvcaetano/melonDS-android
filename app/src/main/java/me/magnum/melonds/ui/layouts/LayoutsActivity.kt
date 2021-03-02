package me.magnum.melonds.ui.layouts

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.ViewGroup
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isInvisible
import androidx.lifecycle.Observer
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import dagger.hilt.android.AndroidEntryPoint
import me.magnum.melonds.R
import me.magnum.melonds.databinding.ActivityLayoutsBinding
import me.magnum.melonds.databinding.ItemLayoutBinding
import me.magnum.melonds.domain.model.LayoutConfiguration
import me.magnum.melonds.ui.layouteditor.LayoutEditorActivity
import java.util.*

@AndroidEntryPoint
class LayoutsActivity : AppCompatActivity() {
    private val viewModel: LayoutsViewModel by viewModels()
    private lateinit var binding: ActivityLayoutsBinding
    private lateinit var layoutsAdapter: LayoutsAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLayoutsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        layoutsAdapter = LayoutsAdapter({
            viewModel.setSelectedLayout(it)
        }) {
            editLayout(it)
        }

        binding.listLayouts.apply {
            val listLayoutManager = LinearLayoutManager(context)
            layoutManager = listLayoutManager
            addItemDecoration(DividerItemDecoration(context, listLayoutManager.orientation))
            adapter = layoutsAdapter
        }

        layoutsAdapter.setSelectedLayoutId(viewModel.getSelectedLayoutId())
        viewModel.getLayouts().observe(this, Observer {
            layoutsAdapter.setLayouts(it)
        })
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        if (menu == null)
            return super.onCreateOptionsMenu(menu)

        menuInflater.inflate(R.menu.layouts_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.action_layouts_add -> createLayout()
            else -> return super.onOptionsItemSelected(item)
        }
        return true
    }

    private fun editLayout(layout: LayoutConfiguration) {
        layout.id?.let {
            val intent = Intent(this, LayoutEditorActivity::class.java)
            intent.putExtra(LayoutEditorActivity.KEY_LAYOUT_ID, it.toString())
            startActivity(intent)
        }
    }

    private fun createLayout() {
        val intent = Intent(this, LayoutEditorActivity::class.java)
        startActivity(intent)
    }

    private class LayoutsAdapter(private val onLayoutSelected: (LayoutConfiguration) -> Unit, private val onEditLayout: (LayoutConfiguration) -> Unit) : RecyclerView.Adapter<LayoutsAdapter.ViewHolder>() {
        class ViewHolder(private val binding: ItemLayoutBinding) : RecyclerView.ViewHolder(binding.root) {
            lateinit var layoutConfiguration: LayoutConfiguration
                private set

            init {
                binding.root.setOnClickListener {
                    binding.radioLayoutSelected.toggle()
                }
            }

            fun setLayout(layout: LayoutConfiguration, isSelected: Boolean) {
                layoutConfiguration = layout
                binding.radioLayoutSelected.isChecked = isSelected
                binding.textLayoutName.text = layout.name
                binding.buttonLayoutEdit.isInvisible = layout.type != LayoutConfiguration.LayoutType.CUSTOM
            }
        }

        private var selectedLayoutId: UUID? = null
        private val layouts = mutableListOf<LayoutConfiguration>()

        fun setLayouts(newLayouts: List<LayoutConfiguration>) {
            layouts.clear()
            layouts.addAll(newLayouts)
            notifyDataSetChanged()
        }

        fun setSelectedLayoutId(layoutId: UUID) {
            if (layoutId == selectedLayoutId) {
                return
            }

            val previousLayoutIndex = layouts.indexOfFirst { it.id == selectedLayoutId }
            val newLayoutIndex = layouts.indexOfFirst { it.id == layoutId }
            selectedLayoutId = layoutId

            if (previousLayoutIndex >= 0) {
                notifyItemChanged(previousLayoutIndex)
            }
            if (newLayoutIndex >= 0) {
                notifyItemChanged(newLayoutIndex)
            }
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val binding = ItemLayoutBinding.inflate(LayoutInflater.from(parent.context), parent, false)

            return ViewHolder(binding).also { holder ->
                binding.radioLayoutSelected.setOnCheckedChangeListener { _, isChecked ->
                    if (isChecked) {
                        holder.layoutConfiguration.id?.let {
                            setSelectedLayoutId(it)
                        }
                        onLayoutSelected(holder.layoutConfiguration)
                    }
                }
                binding.buttonLayoutEdit.setOnClickListener {
                    onEditLayout(holder.layoutConfiguration)
                }
            }
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val layout = layouts[position]
            holder.setLayout(layout, layout.id == selectedLayoutId)
        }

        override fun getItemCount(): Int {
            return layouts.size
        }
    }
}