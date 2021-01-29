package me.magnum.melonds.ui.romlist

import android.Manifest
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.widget.ImageViewCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Observer
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.android.synthetic.main.item_rom_base.view.*
import kotlinx.android.synthetic.main.item_rom_configurable.view.*
import kotlinx.android.synthetic.main.rom_list_fragment.*
import me.magnum.melonds.R
import me.magnum.melonds.domain.model.*
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

    private val romListViewModel: RomListViewModel by activityViewModels()
    private lateinit var romListAdapter: RomListAdapter
    private val microphonePermissionLauncher by lazy {
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            microphonePermissionListener?.invoke(granted)
        }
    }

    private var romSelectedListener: ((Rom) -> Unit)? = null
    private var microphonePermissionListener: ((Boolean) -> Unit)? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.rom_list_fragment, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        swipeRefreshRoms.setOnRefreshListener { romListViewModel.refreshRoms() }

        val allowRomConfiguration = arguments?.getBoolean(KEY_ALLOW_ROM_CONFIGURATION) ?: true
        romListAdapter = RomListAdapter(allowRomConfiguration, requireContext(), object : RomClickListener {
            override fun onRomClicked(rom: Rom) {
                romListViewModel.setRomLastPlayedNow(rom)
                romSelectedListener?.invoke(rom)
            }

            override fun onRomConfigClicked(rom: Rom) {
                var onFilePickedListener: ((Uri) -> Unit)? = null
                val romConfigFilePicker = registerForActivityResult(FilePickerContract()) {
                    if (it != null)
                        onFilePickedListener?.invoke(it)
                }

                RomConfigDialog(requireContext(), rom.name, rom.config.copy(), object : RomConfigDialog.RomConfigDelegate {
                            override fun pickFile(startUri: Uri?, onFilePicked: (Uri) -> Unit) {
                                onFilePickedListener = onFilePicked
                                romConfigFilePicker.launch(startUri)
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
                    }
                    show()
                }
            }
        })

        listRoms.apply {
            val listLayoutManager = LinearLayoutManager(context)
            layoutManager = listLayoutManager
            addItemDecoration(DividerItemDecoration(context, listLayoutManager.orientation))
            adapter = romListAdapter
        }

        romListViewModel.getRomScanningStatus().observe(viewLifecycleOwner, Observer { status ->
            swipeRefreshRoms.isRefreshing = status == RomScanningStatus.SCANNING
            displayEmptyListViewIfRequired()
        })
        romListViewModel.getRoms().observe(viewLifecycleOwner, Observer { roms ->
            romListAdapter.setRoms(roms)
            displayEmptyListViewIfRequired()
        })
    }

    private fun displayEmptyListViewIfRequired() {
        val isScanning = swipeRefreshRoms.isRefreshing
        val emptyViewVisible = !isScanning && romListAdapter.itemCount == 0
        textRomListEmpty.visibility = if (emptyViewVisible) View.VISIBLE else View.GONE
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
                val view = LayoutInflater.from(context).inflate(R.layout.item_rom_configurable, viewGroup, false)
                ConfigurableRomViewHolder(view, listener::onRomClicked, listener::onRomConfigClicked)
            } else {
                val view = LayoutInflater.from(context).inflate(R.layout.item_rom_simple, viewGroup, false)
                RomViewHolder(view, listener::onRomClicked)
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
                    itemView.imageRomIcon.setImageDrawable(iconDrawable)
                } catch (e: Exception) {
                    e.printStackTrace()
                    itemView.imageRomIcon.setImageBitmap(null)
                }
                itemView.textRomName.text = rom.name
                itemView.textRomPath.text = FileUtils.getAbsolutePathFromSAFUri(view!!.context, rom.uri)
            }

            protected fun getRom() = rom
        }

        inner class ConfigurableRomViewHolder(itemView: View, onRomClick: (Rom) -> Unit, onRomConfigClick: (Rom) -> Unit) : RomViewHolder(itemView, onRomClick) {
            init {
                itemView.buttonRomConfig.setOnClickListener {
                    onRomConfigClick(getRom())
                }
            }

            override fun setRom(rom: Rom) {
                super.setRom(rom)
                val configButtonTint = if (rom.config.loadGbaCart())
                    ContextCompat.getColor(context, R.color.romConfigButtonEnabled)
                else
                    ContextCompat.getColor(context, R.color.romConfigButtonDefault)

                ImageViewCompat.setImageTintList(itemView.buttonRomConfig, ColorStateList.valueOf(configButtonTint))
            }
        }
    }

    private interface RomClickListener {
        fun onRomClicked(rom: Rom)
        fun onRomConfigClicked(rom: Rom)
    }
}