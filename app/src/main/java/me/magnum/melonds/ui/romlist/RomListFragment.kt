package me.magnum.melonds.ui.romlist

import android.content.Context
import android.content.Intent
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.drawable.toDrawable
import androidx.core.os.bundleOf
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
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
import me.magnum.melonds.domain.model.RomIconFiltering
import me.magnum.melonds.domain.model.RomScanningStatus
import me.magnum.melonds.domain.model.rom.Rom
import me.magnum.melonds.extensions.setViewEnabledRecursive
import me.magnum.melonds.parcelables.RomParcelable
import me.magnum.melonds.ui.romdetails.RomDetailsActivity
import me.magnum.melonds.ui.romlist.RomListFragment.RomListAdapter.RomViewHolder

@AndroidEntryPoint
class RomListFragment : Fragment() {
    companion object {
        private const val KEY_ALLOW_ROM_CONFIGURATION = "allow_rom_configuration"
        private const val KEY_ROM_ENABLE_CRITERIA = "rom_enable_criteria"

        fun newInstance(allowRomConfiguration: Boolean, enableCriteria: RomEnableCriteria): RomListFragment {
            return RomListFragment().also {
                it.arguments = bundleOf(
                    KEY_ALLOW_ROM_CONFIGURATION to allowRomConfiguration,
                    KEY_ROM_ENABLE_CRITERIA to enableCriteria.toString(),
                )
            }
        }
    }

    enum class RomEnableCriteria {
        ENABLE_ALL,
        ENABLE_NON_DSIWARE,
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
        val romEnableCriteria = arguments?.getString(KEY_ROM_ENABLE_CRITERIA)?.let { RomEnableCriteria.valueOf(it) } ?: RomEnableCriteria.ENABLE_ALL

        romListAdapter = RomListAdapter(
            allowRomConfiguration = allowRomConfiguration,
            context = requireContext(),
            coroutineScope = lifecycleScope,
            listener = object : RomClickListener {
                override fun onRomClicked(rom: Rom) {
                    romListViewModel.setRomLastPlayedNow(rom)
                    romSelectedListener?.invoke(rom)
                }

                override fun onRomConfigClicked(rom: Rom) {
                    val intent = Intent(requireContext(), RomDetailsActivity::class.java).apply {
                        putExtra(RomDetailsActivity.KEY_ROM, RomParcelable(rom))
                    }
                    startActivity(intent)
                }
            },
            romEnabledFilter = buildRomEnabledFilter(romEnableCriteria),
        )

        binding.listRoms.apply {
            val listLayoutManager = LinearLayoutManager(context)
            layoutManager = listLayoutManager
            addItemDecoration(DividerItemDecoration(context, listLayoutManager.orientation))
            adapter = romListAdapter
        }

        lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                romListViewModel.romScanningStatus.collectLatest { status ->
                    binding.swipeRefreshRoms.isRefreshing = status == RomScanningStatus.SCANNING
                    displayEmptyListViewIfRequired()
                }
            }
        }

        lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                romListViewModel.roms.filterNotNull().collectLatest { roms ->
                    romListAdapter.setRoms(roms)
                    displayEmptyListViewIfRequired()
                }
            }
        }

        lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                romListViewModel.onRomIconFilteringChanged.collectLatest {
                    romListAdapter.updateIcons()
                }
            }
        }
    }

    private fun displayEmptyListViewIfRequired() {
        val isScanning = binding.swipeRefreshRoms.isRefreshing
        val emptyViewVisible = !isScanning && romListViewModel.roms.value?.isEmpty() == true
        binding.textRomListEmpty.isVisible = emptyViewVisible
    }

    private fun buildRomEnabledFilter(romEnableCriteria: RomEnableCriteria): RomEnabledFilter {
        return when (romEnableCriteria) {
            RomEnableCriteria.ENABLE_ALL -> RomEnabledFilter { true }
            RomEnableCriteria.ENABLE_NON_DSIWARE -> RomEnabledFilter { !it.isDsiWareTitle}
        }
    }

    fun setRomSelectedListener(listener: (Rom) -> Unit) {
        romSelectedListener = listener
    }

    private inner class RomListAdapter(
        private val allowRomConfiguration: Boolean,
        private val context: Context,
        private val coroutineScope: CoroutineScope,
        private val listener: RomClickListener,
        private val romEnabledFilter: RomEnabledFilter,
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
            val isRomEnabled = romEnabledFilter.isRomEnabled(rom)
            romViewHolder.setRom(rom, isRomEnabled)
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
            private val imagePlatformLogo = itemView.findViewById<ImageView>(R.id.logoPlatform)

            private lateinit var rom: Rom
            private var romIconLoadJob: Job? = null

            init {
                itemView.setOnClickListener { onRomClick(rom) }
            }

            fun cleanup() {
                romIconLoadJob?.cancel()
            }

            open fun setRom(rom: Rom, isEnabled: Boolean) {
                this.rom = rom
                textViewRomName.text = rom.config.customName ?: rom.name
                textViewRomPath.text = rom.fileName
                imageViewRomIcon.setImageDrawable(null)
                imagePlatformLogo.isVisible = rom.isDsiWareTitle

                val platformDrawable = if (rom.isDsiWareTitle) {
                     ResourcesCompat.getDrawable(itemView.resources, R.drawable.logo_dsiware, null)
                } else {
                    null
                }

                if (platformDrawable != null && !isEnabled) {
                    platformDrawable.apply {
                        colorFilter = ColorMatrixColorFilter(ColorMatrix().apply { setSaturation(0f) })
                        alpha = 127
                    }
                }

                imagePlatformLogo.setImageDrawable(platformDrawable)

                romIconLoadJob = coroutineScope.launch {
                    val romIcon = romListViewModel.getRomIcon(rom)
                    val iconDrawable = romIcon.bitmap?.toDrawable(itemView.resources)?.apply {
                        paint.isFilterBitmap = romIcon.filtering == RomIconFiltering.LINEAR
                        if (isEnabled) {
                            colorFilter = null
                            alpha = 255
                        } else {
                            colorFilter = ColorMatrixColorFilter(ColorMatrix().apply { setSaturation(0f) })
                            alpha = 127
                        }
                    }
                    imageViewRomIcon.setImageDrawable(iconDrawable)
                }

                itemView.setViewEnabledRecursive(isEnabled)
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

            override fun setRom(rom: Rom, isEnabled: Boolean) {
                super.setRom(rom, isEnabled)
                imageViewButtonRomConfig.isGone = rom.isDsiWareTitle
            }
        }

        inner class RomsDiffUtilCallback(private val oldRoms: List<Rom>, private val newRoms: List<Rom>) : DiffUtil.Callback() {
            override fun getOldListSize(): Int = oldRoms.size

            override fun getNewListSize(): Int = newRoms.size

            override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
                return oldRoms[oldItemPosition].uri == newRoms[newItemPosition].uri
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

    fun interface RomEnabledFilter {
        fun isRomEnabled(rom: Rom): Boolean
    }
}