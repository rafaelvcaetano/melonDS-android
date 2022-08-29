package me.magnum.melonds.ui.romlist

import android.content.Context
import android.graphics.drawable.BitmapDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import me.magnum.melonds.R
import me.magnum.melonds.databinding.ItemRomConfigurableBinding
import me.magnum.melonds.databinding.ItemRomSimpleBinding
import me.magnum.melonds.databinding.RomListFragmentBinding
import me.magnum.melonds.domain.model.Rom
import me.magnum.melonds.domain.model.RomIconFiltering
import me.magnum.melonds.domain.model.RomScanningStatus
import me.magnum.melonds.ui.romlist.RomListFragment.RomListAdapter.RomViewHolder

@AndroidEntryPoint
class RomListFragment : Fragment() {
    companion object {
        private const val KEY_ALLOW_ROM_CONFIGURATION = "allow_rom_configuration"

        fun newInstance(allowRomConfiguration: Boolean): RomListFragment {
            return RomListFragment().also {
                it.arguments = bundleOf(
                    KEY_ALLOW_ROM_CONFIGURATION to allowRomConfiguration
                )
            }
        }
    }

    private lateinit var binding: RomListFragmentBinding
    private val romListViewModel: RomListViewModel by activityViewModels()
    private lateinit var romListAdapter: RomListAdapter

    private var romSelectedListener: ((Rom) -> Unit)? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = RomListFragmentBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.swipeRefreshRoms.setOnRefreshListener { romListViewModel.refreshRoms() }

        val allowRomConfiguration = arguments?.getBoolean(KEY_ALLOW_ROM_CONFIGURATION) ?: true
        romListAdapter = RomListAdapter(allowRomConfiguration, requireContext(), lifecycleScope, object : RomClickListener {
            override fun onRomClicked(rom: Rom) {
                romListViewModel.setRomLastPlayedNow(rom)
                romSelectedListener?.invoke(rom)
            }

            override fun onRomConfigClicked(rom: Rom) {
                RomConfigDialog.newInstance(rom.name, rom.copy()).show(parentFragmentManager, null)
            }
        })

        binding.listRoms.apply {
            val listLayoutManager = LinearLayoutManager(context)
            layoutManager = listLayoutManager
            addItemDecoration(DividerItemDecoration(context, listLayoutManager.orientation))
            adapter = romListAdapter
        }

        lifecycleScope.launchWhenStarted {
            romListViewModel.romScanningStatus.collectLatest { status ->
                binding.swipeRefreshRoms.isRefreshing = status == RomScanningStatus.SCANNING
                displayEmptyListViewIfRequired()
            }
        }

        lifecycleScope.launchWhenStarted {
            romListViewModel.roms.filterNotNull().collectLatest { roms ->
                romListAdapter.setRoms(roms)
                displayEmptyListViewIfRequired()
            }
        }

        lifecycleScope.launchWhenStarted {
            romListViewModel.onRomIconFilteringChanged.collectLatest {
                romListAdapter.updateIcons()
            }
        }
    }

    private fun displayEmptyListViewIfRequired() {
        val isScanning = binding.swipeRefreshRoms.isRefreshing
        val emptyViewVisible = !isScanning && romListViewModel.roms.value?.size == 0
        binding.textRomListEmpty.isVisible = emptyViewVisible
    }

    fun setRomSelectedListener(listener: (Rom) -> Unit) {
        romSelectedListener = listener
    }

    private inner class RomListAdapter(
        private val allowRomConfiguration: Boolean,
        private val context: Context,
        private val coroutineScope: CoroutineScope,
        private val listener: RomClickListener
    ) : RecyclerView.Adapter<RomViewHolder>() {

        private val roms: ArrayList<Rom> = ArrayList()

        fun setRoms(roms: List<Rom>) {
            val result = DiffUtil.calculateDiff(RomsDiffUtilCallback(this.roms, roms))
            result.dispatchUpdatesTo(this)

            this.roms.clear()
            this.roms.addAll(roms)
        }

        fun updateIcons() {
            val diff = DiffUtil.calculateDiff(RomIconDiffUtilCallback(this.roms.size))
            diff.dispatchUpdatesTo(this)
        }

        override fun onCreateViewHolder(viewGroup: ViewGroup, i: Int): RomViewHolder {
            return if (allowRomConfiguration) {
                val binding = ItemRomConfigurableBinding.inflate(LayoutInflater.from(context), viewGroup, false)
                ConfigurableRomViewHolder(binding.root, lifecycleScope, listener::onRomClicked, listener::onRomConfigClicked)
            } else {
                val binding = ItemRomSimpleBinding.inflate(LayoutInflater.from(context), viewGroup, false)
                RomViewHolder(binding.root, coroutineScope, listener::onRomClicked)
            }
        }

        override fun onBindViewHolder(romViewHolder: RomViewHolder, i: Int) {
            val rom = roms[i]
            romViewHolder.setRom(rom)
        }

        override fun onViewRecycled(holder: RomViewHolder) {
            holder.cleanup()
        }

        override fun getItemCount(): Int {
            return roms.size
        }

        open inner class RomViewHolder(itemView: View, private val coroutineScope: CoroutineScope, onRomClick: (Rom) -> Unit) : RecyclerView.ViewHolder(itemView) {
            private val imageViewRomIcon = itemView.findViewById<ImageView>(R.id.imageRomIcon)
            private val textViewRomName = itemView.findViewById<TextView>(R.id.textRomName)
            private val textViewRomPath = itemView.findViewById<TextView>(R.id.textRomPath)

            private lateinit var rom: Rom
            private var romIconLoadJob: Job? = null

            init {
                itemView.setOnClickListener {
                    onRomClick(rom)
                }
            }

            fun cleanup() {
                romIconLoadJob?.cancel()
            }

            open fun setRom(rom: Rom) {
                this.rom = rom
                textViewRomName.text = rom.name
                textViewRomPath.text = rom.fileName
                imageViewRomIcon.setImageDrawable(null)

                romIconLoadJob = coroutineScope.launch {
                    val romIcon = romListViewModel.getRomIcon(rom)
                    val iconDrawable = BitmapDrawable(itemView.resources, romIcon.bitmap)
                    iconDrawable.paint.isFilterBitmap = romIcon.filtering == RomIconFiltering.LINEAR
                    imageViewRomIcon.setImageDrawable(iconDrawable)
                }
            }

            protected fun getRom() = rom
        }

        inner class ConfigurableRomViewHolder(
            itemView: View,
            coroutineScope: CoroutineScope,
            onRomClick: (Rom) -> Unit,
            onRomConfigClick: (Rom) -> Unit
        ) : RomViewHolder(itemView, coroutineScope, onRomClick) {

            private val imageViewButtonRomConfig = itemView.findViewById<ImageView>(R.id.buttonRomConfig)

            init {
                imageViewButtonRomConfig.setOnClickListener {
                    onRomConfigClick(getRom())
                }
            }
        }

        inner class RomsDiffUtilCallback(private val oldRoms: List<Rom>, private val newRoms: List<Rom>) : DiffUtil.Callback() {
            override fun getOldListSize(): Int = oldRoms.size

            override fun getNewListSize(): Int = newRoms.size

            override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
                return oldRoms[oldItemPosition] == newRoms[newItemPosition]
            }

            override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
                return oldRoms[oldItemPosition] == newRoms[newItemPosition]
            }
        }

        /**
         * [DiffUtil] callback used to update the ROM icons in the adapter. This callback doesn't compare any item and has a fixed output. To force the icons to update, the
         * items are always assume to be the same but with different content.
         */
        inner class RomIconDiffUtilCallback(private val romCount: Int) : DiffUtil.Callback() {
            override fun getOldListSize(): Int = romCount

            override fun getNewListSize(): Int = romCount

            override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean = true

            override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean = false
        }
    }

    private interface RomClickListener {
        fun onRomClicked(rom: Rom)
        fun onRomConfigClicked(rom: Rom)
    }
}