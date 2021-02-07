package me.magnum.melonds.ui.romlist

import android.Manifest
import android.content.Context
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Observer
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import dagger.hilt.android.AndroidEntryPoint
import me.magnum.melonds.R
import me.magnum.melonds.databinding.ItemRomConfigurableBinding
import me.magnum.melonds.databinding.ItemRomSimpleBinding
import me.magnum.melonds.databinding.RomListFragmentBinding
import me.magnum.melonds.domain.model.Rom
import me.magnum.melonds.domain.model.RomConfig
import me.magnum.melonds.domain.model.RomIconFiltering
import me.magnum.melonds.domain.model.RomScanningStatus
import me.magnum.melonds.ui.romlist.RomConfigDialog.OnRomConfigSavedListener
import me.magnum.melonds.ui.romlist.RomListFragment.RomListAdapter.RomViewHolder
import me.magnum.melonds.utils.FilePickerContract
import me.magnum.melonds.utils.FileUtils
import java.util.*

@AndroidEntryPoint
class RomListFragment : Fragment() {
    companion object {
        private const val KEY_ALLOW_ROM_CONFIGURATION = "allow_rom_configuration"

        fun newInstance(allowRomConfiguration: Boolean): RomListFragment {
            return RomListFragment().also {
                it.arguments = Bundle().apply {
                    putBoolean(KEY_ALLOW_ROM_CONFIGURATION, allowRomConfiguration)
                }
            }
        }
    }

    private lateinit var binding: RomListFragmentBinding
    private val romListViewModel: RomListViewModel by activityViewModels()
    private lateinit var romListAdapter: RomListAdapter

    private var romSelectedListener: ((Rom) -> Unit)? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        binding = RomListFragmentBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.swipeRefreshRoms.setOnRefreshListener { romListViewModel.refreshRoms() }

        val allowRomConfiguration = arguments?.getBoolean(KEY_ALLOW_ROM_CONFIGURATION) ?: true
        romListAdapter = RomListAdapter(allowRomConfiguration, requireContext(), object : RomClickListener {
            override fun onRomClicked(rom: Rom) {
                romListViewModel.setRomLastPlayedNow(rom)
                romSelectedListener?.invoke(rom)
            }

            override fun onRomConfigClicked(rom: Rom) {
                var onFilePickedListener: ((Uri) -> Unit)? = null
                var microphonePermissionListener: ((Boolean) -> Unit)? = null

                val romConfigFilePicker = registerForActivityResult(FilePickerContract()) {
                    if (it != null)
                        onFilePickedListener?.invoke(it)
                }
                val microphonePermissionLauncher by lazy {
                    registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
                        microphonePermissionListener?.invoke(granted)
                    }
                }

                RomConfigDialog(requireContext(), rom.name, rom.config.copy(), object : RomConfigDialog.RomConfigDelegate {
                            override fun pickFile(startUri: Uri?, onFilePicked: (Uri) -> Unit) {
                                onFilePickedListener = onFilePicked
                                romConfigFilePicker.launch(Pair(startUri, null))
                            }

                            override fun requestMicrophonePermission(onPermissionResult: (Boolean) -> Unit) {
                                microphonePermissionListener = onPermissionResult
                                microphonePermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            }
                }).apply {
                    setOnRomConfigSaveListener(object : OnRomConfigSavedListener {
                        override fun onRomConfigSaved(romConfig: RomConfig) {
                            romListViewModel.updateRomConfig(rom, romConfig)
                        }
                    })
                    setOnDismissListener {
                        romConfigFilePicker.unregister()
                        microphonePermissionLauncher.unregister()
                    }
                    show()
                }
            }
        })

        binding.listRoms.apply {
            val listLayoutManager = LinearLayoutManager(context)
            layoutManager = listLayoutManager
            addItemDecoration(DividerItemDecoration(context, listLayoutManager.orientation))
            adapter = romListAdapter
        }

        romListViewModel.getRomScanningStatus().observe(viewLifecycleOwner, Observer { status ->
            binding.swipeRefreshRoms.isRefreshing = status == RomScanningStatus.SCANNING
            displayEmptyListViewIfRequired()
        })
        romListViewModel.getRoms().observe(viewLifecycleOwner, Observer { roms ->
            romListAdapter.setRoms(roms)
            displayEmptyListViewIfRequired()
        })
    }

    private fun displayEmptyListViewIfRequired() {
        val isScanning = binding.swipeRefreshRoms.isRefreshing
        val emptyViewVisible = !isScanning && romListAdapter.itemCount == 0
        binding.textRomListEmpty.visibility = if (emptyViewVisible) View.VISIBLE else View.GONE
    }

    fun setRomSelectedListener(listener: (Rom) -> Unit) {
        romSelectedListener = listener
    }

    private inner class RomListAdapter(private val allowRomConfiguration: Boolean, private val context: Context, private val listener: RomClickListener) : RecyclerView.Adapter<RomViewHolder>() {
        private val roms: ArrayList<Rom> = ArrayList()

        fun setRoms(roms: List<Rom>) {
            this.roms.clear()
            this.roms.addAll(roms)
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(viewGroup: ViewGroup, i: Int): RomViewHolder {
            return if (allowRomConfiguration) {
                val binding = ItemRomConfigurableBinding.inflate(LayoutInflater.from(context), viewGroup, false)
                ConfigurableRomViewHolder(binding.root, listener::onRomClicked, listener::onRomConfigClicked)
            } else {
                val binding = ItemRomSimpleBinding.inflate(LayoutInflater.from(context), viewGroup, false)
                RomViewHolder(binding.root, listener::onRomClicked)
            }
        }

        override fun onBindViewHolder(romViewHolder: RomViewHolder, i: Int) {
            val rom = roms[i]
            romViewHolder.setRom(rom)
        }

        override fun getItemCount(): Int {
            return roms.size
        }

        open inner class RomViewHolder(itemView: View, onRomClick: (Rom) -> Unit) : RecyclerView.ViewHolder(itemView) {
            private val imageViewRomIcon = itemView.findViewById<ImageView>(R.id.imageRomIcon)
            private val textViewRomName = itemView.findViewById<TextView>(R.id.textRomName)
            private val textViewRomPath = itemView.findViewById<TextView>(R.id.textRomPath)

            private lateinit var rom: Rom

            init {
                itemView.setOnClickListener {
                    onRomClick(rom)
                }
            }

            open fun setRom(rom: Rom) {
                this.rom = rom

                try {
                    val romIcon = romListViewModel.getRomIcon(rom)
                    val iconDrawable = BitmapDrawable(itemView.resources, romIcon.bitmap)
                    iconDrawable.paint.isFilterBitmap = romIcon.filtering == RomIconFiltering.LINEAR
                    imageViewRomIcon.setImageDrawable(iconDrawable)
                } catch (e: Exception) {
                    e.printStackTrace()
                    imageViewRomIcon.setImageBitmap(null)
                }
                textViewRomName.text = rom.name
                textViewRomPath.text = FileUtils.getAbsolutePathFromSAFUri(view!!.context, rom.uri)
            }

            protected fun getRom() = rom
        }

        inner class ConfigurableRomViewHolder(itemView: View, onRomClick: (Rom) -> Unit, onRomConfigClick: (Rom) -> Unit) : RomViewHolder(itemView, onRomClick) {
            private val imageViewButtonRomConfig = itemView.findViewById<ImageView>(R.id.buttonRomConfig)

            init {
                imageViewButtonRomConfig.setOnClickListener {
                    onRomConfigClick(getRom())
                }
            }
        }
    }

    private interface RomClickListener {
        fun onRomClicked(rom: Rom)
        fun onRomConfigClicked(rom: Rom)
    }
}