package me.magnum.melonds.ui.romlist

import android.Manifest
import android.app.SearchManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.os.Bundle
import android.view.*
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.SearchView
import androidx.core.content.ContextCompat
import androidx.core.widget.ImageViewCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
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
import me.magnum.melonds.model.Rom
import me.magnum.melonds.model.RomConfig
import me.magnum.melonds.model.RomScanningStatus
import me.magnum.melonds.repositories.SettingsRepository
import me.magnum.melonds.ui.SettingsActivity
import me.magnum.melonds.ui.romlist.RomConfigDialog.OnRomConfigSavedListener
import me.magnum.melonds.ui.romlist.RomListFragment.RomListAdapter.RomViewHolder
import me.magnum.melonds.utils.ConfigurationUtils.ConfigurationDirStatus
import me.magnum.melonds.utils.ConfigurationUtils.checkConfigurationDirectory
import me.magnum.melonds.utils.RomProcessor
import java.io.File
import java.util.*

class RomListFragment : Fragment() {
    companion object {
        private const val REQUEST_STORAGE_PERMISSION = 1

        fun newInstance(): RomListFragment {
            return RomListFragment()
        }
    }

    private var romSelectedListener: ((Rom) -> Unit)? = null
    private val romListViewModel: RomListViewModel by viewModels { ServiceLocator[ViewModelProvider.Factory::class] }
    private lateinit var settingsRepository: SettingsRepository

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        setHasOptionsMenu(true)
        return inflater.inflate(R.layout.rom_list_fragment, container, false)
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        settingsRepository = ServiceLocator[SettingsRepository::class]

        swipeRefreshRoms.setOnRefreshListener { updateRomList() }

        val romListAdapter = RomListAdapter(requireContext(), object : RomClickListener {
            override fun onRomClicked(rom: Rom) {
                romSelectedListener?.invoke(rom)
            }

            override fun onRomConfigClicked(rom: Rom) {
                RomConfigDialog(requireContext(), rom.name, rom.config.copy())
                        .setOnRomConfigSaveListener(object : OnRomConfigSavedListener {
                            override fun onRomConfigSaved(romConfig: RomConfig) {
                                romListViewModel.updateRomConfig(rom, romConfig)
                            }
                        })
                        .show()
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
        })
        romListViewModel.getRoms().observe(viewLifecycleOwner, Observer { roms ->
            romListAdapter.setRoms(roms)
        })
    }

    override fun onStart() {
        super.onStart()
        if (!checkConfigDirectorySetup())
            return

        if (!isStoragePermissionGranted())
            requestStoragePermission(false)
    }

    private fun updateRomList() {
        if (!isStoragePermissionGranted())
            return

        if (!isConfigDirectorySetup())
            return

        romListViewModel.refreshRoms()
    }

    private fun isStoragePermissionGranted(): Boolean {
        return ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestStoragePermission(overrideRationaleRequest: Boolean) {
        if (!overrideRationaleRequest && shouldShowRequestPermissionRationale(Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
            AlertDialog.Builder(requireContext())
                    .setTitle(R.string.storage_permission_required)
                    .setMessage(R.string.storage_permission_required_info)
                    .setPositiveButton(R.string.ok) { _, _ -> requestStoragePermission(true) }
                    .show()
        } else {
            requestPermissions(arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE),
                    REQUEST_STORAGE_PERMISSION)
        }
    }

    private fun isConfigDirectorySetup(): Boolean {
        val configDir = settingsRepository.getBiosDirectory()
        val dirStatus = checkConfigurationDirectory(configDir)
        return dirStatus === ConfigurationDirStatus.VALID
    }

    private fun checkConfigDirectorySetup(): Boolean {
        val configDir = settingsRepository.getBiosDirectory()
        when (checkConfigurationDirectory(configDir)) {
            ConfigurationDirStatus.VALID -> return true
            ConfigurationDirStatus.UNSET -> AlertDialog.Builder(requireContext())
                    .setTitle(R.string.bios_dir_not_set)
                    .setMessage(R.string.bios_dir_not_set_info)
                    .setPositiveButton(R.string.ok) { _, _ ->
                        val intent = Intent(context, SettingsActivity::class.java)
                        startActivity(intent)
                    }
                    .show()
            ConfigurationDirStatus.INVALID -> AlertDialog.Builder(requireContext())
                    .setTitle(R.string.incorrect_bios_dir)
                    .setMessage(R.string.incorrect_bios_dir_info)
                    .setPositiveButton(R.string.ok) { _, _ ->
                        val intent = Intent(context, SettingsActivity::class.java)
                        startActivity(intent)
                    }
                    .show()
        }
        return false
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        if (requestCode != REQUEST_STORAGE_PERMISSION)
            return

        if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED)
            updateRomList()
        else
            Toast.makeText(context, getString(R.string.info_no_storage_permission), Toast.LENGTH_LONG).show()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.action_rom_list_refresh -> {
                updateRomList()
                return true
            }
            R.id.action_settings -> {
                val intent = Intent(context, SettingsActivity::class.java)
                startActivity(intent)
                return true
            }
        }
        return false
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.rom_list_menu, menu)

        val searchItem =  menu.findItem(R.id.action_search_roms)
        ContextCompat.getSystemService(requireContext(), SearchManager::class.java)?.let { searchManager ->
            val searchView = searchItem.actionView as SearchView
            searchView.apply {
                queryHint = getString(R.string.hint_search_roms)
                setSearchableInfo(searchManager.getSearchableInfo(requireActivity().componentName))
                setOnQueryTextListener(object : SearchView.OnQueryTextListener {
                    override fun onQueryTextSubmit(query: String?): Boolean {
                        return true
                    }

                    override fun onQueryTextChange(newText: String?): Boolean {
                        romListViewModel.setRomSearchQuery(newText)
                        return true
                    }
                })
            }
        }

        super.onCreateOptionsMenu(menu, inflater)
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

                val romFile = File(rom.path)
                try {
                    val icon = RomProcessor.getRomIcon(romFile)
                    itemView.imageRomIcon.setImageBitmap(icon)
                } catch (e: Exception) {
                    e.printStackTrace()
                    imageRomIcon.setImageBitmap(null)
                }
                itemView.textRomName.text = rom.name
                itemView.textRomPath.text = rom.path

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