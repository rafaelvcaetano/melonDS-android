package me.magnum.melonds.ui.romlist

import android.content.Context
import android.content.res.ColorStateList
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.widget.ImageViewCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.android.synthetic.main.item_rom.*
import kotlinx.android.synthetic.main.item_rom.view.*
import kotlinx.android.synthetic.main.rom_list_fragment.*
import me.magnum.melonds.R
import me.magnum.melonds.ServiceLocator
import me.magnum.melonds.domain.model.Rom
import me.magnum.melonds.domain.model.RomConfig
import me.magnum.melonds.domain.model.RomScanningStatus
import me.magnum.melonds.ui.romlist.RomConfigDialog.OnRomConfigSavedListener
import me.magnum.melonds.ui.romlist.RomListFragment.RomListAdapter.RomViewHolder
import me.magnum.melonds.utils.FilePickerContract
import me.magnum.melonds.utils.FileUtils
import me.magnum.melonds.utils.RomIconProvider
import java.util.*

class RomListFragment : Fragment() {
    companion object {
        fun newInstance(): RomListFragment {
            return RomListFragment()
        }
    }

    private var romSelectedListener: ((Rom) -> Unit)? = null
    private val romListViewModel: RomListViewModel by activityViewModels { ServiceLocator[ViewModelProvider.Factory::class] }
    private val romIconProvider: RomIconProvider by lazy { ServiceLocator[RomIconProvider::class] }
    private lateinit var romListAdapter: RomListAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.rom_list_fragment, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        swipeRefreshRoms.setOnRefreshListener { romListViewModel.refreshRoms() }

        romListAdapter = RomListAdapter(requireContext(), object : RomClickListener {
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

                RomConfigDialog(requireContext(), rom.name, rom.config.copy(), object : RomConfigDialog.FilePicker {
                            override fun pickFile(startUri: Uri?, onFilePicked: (Uri) -> Unit) {
                                onFilePickedListener = onFilePicked
                                romConfigFilePicker.launch(startUri)
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

    private inner class RomListAdapter(private val context: Context, private val listener: RomClickListener) : RecyclerView.Adapter<RomViewHolder>() {
        private val roms: ArrayList<Rom> = ArrayList()

        fun setRoms(roms: List<Rom>) {
            this.roms.clear()
            this.roms.addAll(roms)
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(viewGroup: ViewGroup, i: Int): RomViewHolder {
            val view = LayoutInflater.from(context).inflate(R.layout.item_rom, viewGroup, false)
            return RomViewHolder(view, listener::onRomClicked, listener::onRomConfigClicked)
        }

        override fun onBindViewHolder(romViewHolder: RomViewHolder, i: Int) {
            val rom = roms[i]
            romViewHolder.setRom(rom)
        }

        override fun getItemCount(): Int {
            return roms.size
        }

        inner class RomViewHolder(itemView: View, onRomClick: (Rom) -> Unit, onRomConfigClick: (Rom) -> Unit) : RecyclerView.ViewHolder(itemView) {
            private lateinit var rom: Rom

            init {
                itemView.setOnClickListener {
                    onRomClick(rom)
                }
                itemView.buttonRomConfig.setOnClickListener {
                    onRomConfigClick(rom)
                }
            }

            fun setRom(rom: Rom) {
                this.rom = rom

                try {
                    val icon = romIconProvider.getRomIcon(rom)
                    itemView.imageRomIcon.setImageBitmap(icon)
                } catch (e: Exception) {
                    e.printStackTrace()
                    imageRomIcon.setImageBitmap(null)
                }
                itemView.textRomName.text = rom.name
                itemView.textRomPath.text = FileUtils.getAbsolutePathFromSAFUri(view!!.context, rom.uri)

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