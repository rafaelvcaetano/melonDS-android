package me.magnum.melonds.ui.layouts

import android.content.Intent
import android.os.Bundle
import android.view.*
import android.widget.PopupMenu
import androidx.core.view.MenuProvider
import androidx.core.view.isInvisible
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.snackbar.Snackbar
import me.magnum.melonds.R
import me.magnum.melonds.databinding.FragmentLayoutListBinding
import me.magnum.melonds.databinding.ItemLayoutBinding
import me.magnum.melonds.domain.model.LayoutConfiguration
import me.magnum.melonds.ui.layouteditor.LayoutEditorActivity
import java.util.*

abstract class BaseLayoutsFragment : Fragment() {
    enum class LayoutSelectionReason {
        /**
         * The layout was selected by the user.
         */
        BY_USER,

        /**
         * The layout was selected as a fallback because the previous one became unavailable (most likely it was deleted).
         */
        BY_FALLBACK
    }

    private val viewModel by lazy { getFragmentViewModel() }
    private lateinit var binding: FragmentLayoutListBinding
    private lateinit var layoutsAdapter: LayoutsAdapter

    private var layoutSelectedListener: ((UUID?, LayoutSelectionReason) -> Unit)? = null

    abstract fun getFragmentViewModel(): BaseLayoutsViewModel

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        binding = FragmentLayoutListBinding.inflate(inflater, container, false)
        requireActivity().addMenuProvider(object : MenuProvider {
            override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
                menuInflater.inflate(R.menu.layouts_menu, menu)
            }

            override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
                return handleOptionItemSelected(menuItem)
            }
        }, viewLifecycleOwner)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        layoutsAdapter = LayoutsAdapter(object : LayoutInteractionListener {
            override fun onLayoutSelected(layout: LayoutConfiguration) {
                this@BaseLayoutsFragment.onLayoutSelected(layout)
            }

            override fun onEditLayout(layout: LayoutConfiguration) {
                this@BaseLayoutsFragment.editLayout(layout)
            }

            override fun onDeleteLayout(layout: LayoutConfiguration) {
                this@BaseLayoutsFragment.deleteLayout(layout)
            }
        })

        binding.listLayouts.apply {
            val listLayoutManager = LinearLayoutManager(context)
            layoutManager = listLayoutManager
            addItemDecoration(DividerItemDecoration(context, listLayoutManager.orientation))
            adapter = layoutsAdapter
        }

        layoutsAdapter.setSelectedLayoutId(viewModel.getSelectedLayoutId())
        viewModel.getLayouts().observe(viewLifecycleOwner) {
            // If the currently selected layout is not found in the layout list, select fallback layout
            if (it.find { layout -> layout.id == viewModel.getSelectedLayoutId() } == null) {
                selectFallbackLayout()
            }

            layoutsAdapter.setLayouts(it)
        }
    }

    private fun handleOptionItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.action_layouts_add -> createLayout()
            else -> return false
        }
        return true
    }

    fun setOnLayoutSelectedListener(listener: (UUID?, LayoutSelectionReason) -> Unit) {
        layoutSelectedListener = listener
    }

    private fun onLayoutSelected(layout: LayoutConfiguration) {
        layoutSelectedListener?.invoke(layout.id, LayoutSelectionReason.BY_USER)
    }

    private fun editLayout(layout: LayoutConfiguration) {
        layout.id?.let {
            val intent = Intent(requireContext(), LayoutEditorActivity::class.java)
            intent.putExtra(LayoutEditorActivity.KEY_LAYOUT_ID, it.toString())
            startActivity(intent)
        }
    }

    private fun deleteLayout(layout: LayoutConfiguration) {
        if (layout.id == viewModel.getSelectedLayoutId()) {
            // Deleting the currently selected layout. Select fallback layout
            selectFallbackLayout()
        }

        viewModel.deleteLayout(layout)
        Snackbar.make(binding.root, R.string.layout_deleted, Snackbar.LENGTH_LONG).apply {
            setAction(R.string.undo) {
                viewModel.addLayout(layout)
            }
            show()
        }
    }

    private fun selectFallbackLayout() {
        val defaultLayoutId = getFallbackLayoutId()
        viewModel.setSelectedLayoutId(defaultLayoutId)
        layoutsAdapter.setSelectedLayoutId(defaultLayoutId)
        layoutSelectedListener?.invoke(defaultLayoutId, LayoutSelectionReason.BY_FALLBACK)
    }

    private fun createLayout() {
        val intent = Intent(requireContext(), LayoutEditorActivity::class.java)
        startActivity(intent)
    }

    /**
     * Returns the ID of the fallback layout in case the user deletes the currently selected one.
     */
    abstract fun getFallbackLayoutId(): UUID?

    private class LayoutsAdapter(private val layoutInteractionListener: LayoutInteractionListener) : RecyclerView.Adapter<LayoutsAdapter.ViewHolder>() {
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
                binding.buttonLayoutOptions.isInvisible = layout.type != LayoutConfiguration.LayoutType.CUSTOM
            }
        }

        private var selectedLayoutId: UUID? = null
        private val layouts = mutableListOf<LayoutConfiguration>()

        fun setLayouts(newLayouts: List<LayoutConfiguration>) {
            val result = DiffUtil.calculateDiff(LayoutsDiffUtilCallback(layouts, newLayouts))
            result.dispatchUpdatesTo(this)

            layouts.clear()
            layouts.addAll(newLayouts)
        }

        fun setSelectedLayoutId(layoutId: UUID?) {
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
                        val previousSelectedLayoutId = selectedLayoutId
                        setSelectedLayoutId(holder.layoutConfiguration.id)

                        if (holder.layoutConfiguration.id != previousSelectedLayoutId) {
                            layoutInteractionListener.onLayoutSelected(holder.layoutConfiguration)
                        }
                    }
                }
                binding.buttonLayoutOptions.setOnClickListener {
                    val popup = PopupMenu(parent.context, binding.buttonLayoutOptions)
                    popup.menuInflater.inflate(R.menu.layout_item_menu, popup.menu)
                    popup.setOnMenuItemClickListener {
                        when (it.itemId) {
                            R.id.action_layout_edit -> layoutInteractionListener.onEditLayout(holder.layoutConfiguration)
                            R.id.action_layout_delete -> layoutInteractionListener.onDeleteLayout(holder.layoutConfiguration)
                            else -> return@setOnMenuItemClickListener false
                        }
                        return@setOnMenuItemClickListener true
                    }
                    popup.show()
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

        class LayoutsDiffUtilCallback(private val oldLayouts: List<LayoutConfiguration>, private val newLayouts: List<LayoutConfiguration>) : DiffUtil.Callback() {
            override fun getOldListSize() = oldLayouts.size

            override fun getNewListSize() = newLayouts.size

            override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
                val oldBackground = oldLayouts[oldItemPosition]
                val newBackground = newLayouts[newItemPosition]

                return oldBackground.id == newBackground.id
            }

            override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
                val oldLayout = oldLayouts[oldItemPosition]
                val newLayout = newLayouts[newItemPosition]

                return oldLayout == newLayout
            }
        }
    }

    interface LayoutInteractionListener {
        fun onLayoutSelected(layout: LayoutConfiguration)
        fun onEditLayout(layout: LayoutConfiguration)
        fun onDeleteLayout(layout: LayoutConfiguration)
    }
}