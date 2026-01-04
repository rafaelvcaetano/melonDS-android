package me.magnum.melonds.ui.romlist

import android.content.Context
import android.content.Intent
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.os.Bundle
import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
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
import kotlinx.coroutines.launch
import me.magnum.melonds.R
import me.magnum.melonds.databinding.ItemRomConfigurableBinding
import me.magnum.melonds.databinding.ItemRomSimpleBinding
import me.magnum.melonds.databinding.RomListFragmentBinding
import me.magnum.melonds.domain.model.RomIconFiltering
import me.magnum.melonds.domain.model.RomScanningStatus
import me.magnum.melonds.domain.model.rom.Rom
import me.magnum.melonds.domain.model.rom.RomDirectoryScanStatus
import me.magnum.melonds.extensions.setViewEnabledRecursive
import me.magnum.melonds.parcelables.RomParcelable
import me.magnum.melonds.ui.romdetails.RomDetailsActivity
import me.magnum.melonds.ui.romlist.RomListFragment.RomListAdapter.RomViewHolder

@AndroidEntryPoint
class RomListFragment : Fragment() {
    companion object {
        private const val KEY_ALLOW_ROM_CONFIGURATION = "allow_rom_configuration"
        private const val KEY_ROM_ENABLE_CRITERIA = "rom_enable_criteria"
        private const val VIEW_TYPE_FOLDER = 0
        private const val VIEW_TYPE_ROM_SIMPLE = 1
        private const val VIEW_TYPE_ROM_CONFIGURABLE = 2

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
    private lateinit var backPressedCallback: OnBackPressedCallback
    private var lastBrowserState: RomBrowserUiState? = null
    private var lastDirectoryStatusUi: List<RomListViewModel.DirectoryCacheStatusUi> = emptyList()

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
            listener = object : RomBrowserClickListener {
                override fun onFolderClicked(folder: RomBrowserEntry.Folder) {
                    romListViewModel.openFolder(folder.docId)
                }

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

        backPressedCallback = object : OnBackPressedCallback(false) {
            override fun handleOnBackPressed() {
                romListViewModel.navigateUp()
            }
        }
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, backPressedCallback)

        binding.buttonNavigateUp.setOnClickListener {
            romListViewModel.navigateUp()
        }

        lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                romListViewModel.romScanningStatus.collectLatest { status ->
                    binding.swipeRefreshRoms.isRefreshing = status == RomScanningStatus.SCANNING
                    lastBrowserState?.let { updateEmptyListView(it) }
                }
            }
        }

        lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                romListViewModel.browserState.collectLatest { state ->
                    lastBrowserState = state
                    romListAdapter.setEntries(state.entries)
                    updateNavigationUi(state)
                    updateEmptyListView(state)
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

        lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                romListViewModel.directoryStatusUi.collectLatest {
                    lastDirectoryStatusUi = it
                    renderDirectoryCacheStatus()
                }
            }
        }
    }

    private fun updateEmptyListView(state: RomBrowserUiState) {
        val isScanning = binding.swipeRefreshRoms.isRefreshing
        val emptyViewVisible = !isScanning && state.entries.isEmpty()
        binding.textRomListEmpty.isVisible = emptyViewVisible
    }

    private fun renderDirectoryCacheStatus() {
        val state = lastBrowserState
        val statuses = lastDirectoryStatusUi
        val shouldShow = state?.isAtVirtualRoot == true && state.isSearchActive.not() && statuses.isNotEmpty()

        if (!shouldShow) {
            binding.textDirectoryCacheStatus.isGone = true
            return
        }

        val now = System.currentTimeMillis()
        val cacheLines = statuses.map { status ->
            val timeText = status.lastScanTimestamp?.let {
                DateUtils.getRelativeTimeSpanString(it, now, DateUtils.MINUTE_IN_MILLIS).toString()
            } ?: getString(R.string.rom_cache_status_just_now)

            val statusText = when (status.result) {
                RomDirectoryScanStatus.ScanResult.UPDATED -> getString(R.string.rom_cache_status_updated, timeText)
                RomDirectoryScanStatus.ScanResult.UNCHANGED -> getString(R.string.rom_cache_status_cached, timeText)
                RomDirectoryScanStatus.ScanResult.NOT_SCANNED -> getString(R.string.rom_cache_status_never)
            }

            "${status.directoryName} • $statusText"
        }

        binding.textDirectoryCacheStatus.isVisible = true
        binding.textDirectoryCacheStatus.text = cacheLines.joinToString("\n")
    }

    private fun updateNavigationUi(state: RomBrowserUiState) {
        val isSearch = state.isSearchActive
        val canNavigate = state.canNavigateUp && !isSearch
        binding.buttonNavigateUp.isVisible = canNavigate
        binding.buttonNavigateUp.isEnabled = canNavigate
        backPressedCallback.isEnabled = canNavigate

        val pathText = when {
            isSearch -> getString(R.string.rom_browser_search_results)
            state.isAtVirtualRoot -> getString(R.string.rom_browser_virtual_root)
            state.breadcrumbs.isEmpty() -> getString(R.string.rom_browser_virtual_root)
            else -> state.breadcrumbs.joinToString(" / ")
        }
        binding.textCurrentFolder.text = pathText
        renderDirectoryCacheStatus()
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
        private val listener: RomBrowserClickListener,
        private val romEnabledFilter: RomEnabledFilter,
    ) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

        private val entries: ArrayList<RomBrowserEntry> = ArrayList()

        fun setEntries(entries: List<RomBrowserEntry>) {
            val oldEntries = ArrayList(this.entries)
            val diff = DiffUtil.calculateDiff(BrowserEntriesDiffCallback(oldEntries, entries))
            this.entries.clear()
            this.entries.addAll(entries)
            diff.dispatchUpdatesTo(this)
        }

        fun updateIcons() {
            notifyDataSetChanged()
        }

        override fun getItemCount(): Int = entries.size

        override fun getItemViewType(position: Int): Int {
            return when (entries[position]) {
                is RomBrowserEntry.Folder -> VIEW_TYPE_FOLDER
                is RomBrowserEntry.RomItem -> if (allowRomConfiguration) VIEW_TYPE_ROM_CONFIGURABLE else VIEW_TYPE_ROM_SIMPLE
            }
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            val inflater = LayoutInflater.from(context)
            return when (viewType) {
                VIEW_TYPE_FOLDER -> {
                    val binding = ItemRomSimpleBinding.inflate(inflater, parent, false)
                    FolderViewHolder(binding.root, listener::onFolderClicked)
                }
                VIEW_TYPE_ROM_CONFIGURABLE -> {
                    val binding = ItemRomConfigurableBinding.inflate(inflater, parent, false)
                    ConfigurableRomViewHolder(binding.root, lifecycleScope, listener::onRomClicked, listener::onRomConfigClicked)
                }
                else -> {
                    val binding = ItemRomSimpleBinding.inflate(inflater, parent, false)
                    RomViewHolder(binding.root, coroutineScope, listener::onRomClicked)
                }
            }
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            val entry = entries[position]
            when {
                holder is FolderViewHolder && entry is RomBrowserEntry.Folder -> holder.bind(entry)
                holder is RomViewHolder && entry is RomBrowserEntry.RomItem -> holder.setRom(entry.rom, romEnabledFilter.isRomEnabled(entry.rom))
            }
        }

        override fun onViewRecycled(holder: RecyclerView.ViewHolder) {
            if (holder is RomViewHolder) {
                holder.cleanup()
            }
        }

        private inner class FolderViewHolder(itemView: View, private val onFolderClick: (RomBrowserEntry.Folder) -> Unit) : RecyclerView.ViewHolder(itemView) {
            private val imageViewRomIcon = itemView.findViewById<ImageView>(R.id.imageRomIcon)
            private val textViewRomName = itemView.findViewById<TextView>(R.id.textRomName)
            private val textViewRomPath = itemView.findViewById<TextView>(R.id.textRomPath)
            private val imagePlatformLogo = itemView.findViewById<ImageView>(R.id.logoPlatform)

            private lateinit var folder: RomBrowserEntry.Folder

            init {
                itemView.setOnClickListener { onFolderClick(folder) }
            }

            fun bind(entry: RomBrowserEntry.Folder) {
                folder = entry
                imagePlatformLogo.isGone = true
                val folderDrawable = ResourcesCompat.getDrawable(itemView.resources, R.drawable.ic_folder, null)
                imageViewRomIcon.setImageDrawable(folderDrawable)
                textViewRomName.text = entry.name
                textViewRomPath.text = entry.relativePath
                itemView.setViewEnabledRecursive(true)
            }
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
        }

        inner class BrowserEntriesDiffCallback(
            private val oldEntries: List<RomBrowserEntry>,
            private val newEntries: List<RomBrowserEntry>
        ) : DiffUtil.Callback() {
            override fun getOldListSize(): Int = oldEntries.size

            override fun getNewListSize(): Int = newEntries.size

            override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
                val oldItem = oldEntries[oldItemPosition]
                val newItem = newEntries[newItemPosition]
                return when {
                    oldItem is RomBrowserEntry.Folder && newItem is RomBrowserEntry.Folder -> oldItem.docId == newItem.docId
                    oldItem is RomBrowserEntry.RomItem && newItem is RomBrowserEntry.RomItem -> oldItem.rom.uri == newItem.rom.uri
                    else -> false
                }
            }

            override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
                return oldEntries[oldItemPosition] == newEntries[newItemPosition]
            }
        }
    }

    private interface RomBrowserClickListener {
        fun onFolderClicked(folder: RomBrowserEntry.Folder)
        fun onRomClicked(rom: Rom)
        fun onRomConfigClicked(rom: Rom)
    }

    fun interface RomEnabledFilter {
        fun isRomEnabled(rom: Rom): Boolean
    }
}
