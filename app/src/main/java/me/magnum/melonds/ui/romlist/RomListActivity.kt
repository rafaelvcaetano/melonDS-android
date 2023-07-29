package me.magnum.melonds.ui.romlist

import android.app.SearchManager
import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import androidx.core.content.getSystemService
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.fragment.app.commit
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import dagger.hilt.android.AndroidEntryPoint
import io.noties.markwon.Markwon
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import me.magnum.melonds.R
import me.magnum.melonds.common.Permission
import me.magnum.melonds.common.contracts.DirectoryPickerContract
import me.magnum.melonds.databinding.ActivityRomListBinding
import me.magnum.melonds.domain.model.*
import me.magnum.melonds.ui.dsiwaremanager.DSiWareManagerActivity
import me.magnum.melonds.ui.emulator.EmulatorActivity
import me.magnum.melonds.ui.settings.SettingsActivity
import javax.inject.Inject

@AndroidEntryPoint
class RomListActivity : AppCompatActivity() {
    companion object {
        private const val FRAGMENT_ROM_LIST = "ROM_LIST"
        private const val FRAGMENT_NO_ROM_DIRECTORIES = "NO_ROM_DIRECTORY"
    }

    @Inject lateinit var markwon: Markwon
    private val viewModel: RomListViewModel by viewModels()
    private val updatesViewModel: UpdatesViewModel by viewModels()

    private var downloadProgressDialog: AlertDialog? = null

    private val dsBiosPickerLauncher = registerForActivityResult(DirectoryPickerContract(Permission.READ_WRITE)) { uri ->
        if (uri != null) {
            if (viewModel.setDsBiosDirectory(uri)) {
                selectedRom?.let {
                    loadRom(it)
                } ?: selectedFirmwareConsole?.let {
                    bootFirmware(it)
                }
            }
        }
    }
    private val dsiBiosPickerLauncher = registerForActivityResult(DirectoryPickerContract(Permission.READ_WRITE)) { uri ->
        if (uri != null) {
            if (viewModel.setDsiBiosDirectory(uri)) {
                selectedRom?.let {
                    loadRom(it)
                } ?: selectedFirmwareConsole?.let {
                    bootFirmware(it)
                }
            }
        }
    }

    private var selectedRom: Rom? = null
    private var selectedFirmwareConsole: ConsoleType? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        val binding = ActivityRomListBinding.inflate(layoutInflater)
        setContentView(binding.root)

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.hasSearchDirectories.collectLatest { hasDirectories ->
                    if (hasDirectories) {
                        addRomListFragment()
                    } else {
                        addNoSearchDirectoriesFragment()
                    }
                }
            }
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.invalidDirectoryAccessEvent.collectLatest {
                    showInvalidDirectoryAccessDialog()
                }
            }
        }

        updatesViewModel.getAppUpdate().observe(this) {
            showUpdateAvailableDialog(it)
        }
        updatesViewModel.getDownloadProgress().observe(this) {
            onDownloadProgressUpdated(it)
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.rom_list_menu, menu)

        val searchItem =  menu.findItem(R.id.action_search_roms)
        getSystemService<SearchManager>()?.let { searchManager ->
            val searchView = searchItem.actionView as SearchView
            searchView.apply {
                queryHint = getString(R.string.hint_search_roms)
                setSearchableInfo(searchManager.getSearchableInfo(componentName))
                setOnQueryTextListener(object : SearchView.OnQueryTextListener {
                    override fun onQueryTextSubmit(query: String?): Boolean {
                        return true
                    }

                    override fun onQueryTextChange(newText: String?): Boolean {
                        viewModel.setRomSearchQuery(newText)
                        return true
                    }
                })
            }
        }

        // Fix for action items not appearing after closing the search view
        searchItem.setOnActionExpandListener(object : MenuItem.OnActionExpandListener {
            override fun onMenuItemActionExpand(item: MenuItem): Boolean {
                return true
            }

            override fun onMenuItemActionCollapse(item: MenuItem): Boolean {
                invalidateMenu()
                return true
            }
        })

        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.action_sort_alphabetically -> {
                viewModel.setRomSorting(SortingMode.ALPHABETICALLY)
                return true
            }
            R.id.action_sort_recent -> {
                viewModel.setRomSorting(SortingMode.RECENTLY_PLAYED)
                return true
            }
            R.id.action_boot_firmware_ds -> {
                bootFirmware(ConsoleType.DS)
                return true
            }
            R.id.action_boot_firmware_dsi -> {
                bootFirmware(ConsoleType.DSi)
                return true
            }
            R.id.action_dsiware_manager -> {
                val intent = Intent(this, DSiWareManagerActivity::class.java)
                startActivity(intent)
                return true
            }
            R.id.action_rom_list_refresh -> {
                viewModel.refreshRoms()
                return true
            }
            R.id.action_settings -> {
                val intent = Intent(this, SettingsActivity::class.java)
                startActivity(intent)
                return true
            }
        }
        return false
    }

    private fun addNoSearchDirectoriesFragment() {
        var noRomDirectoriesFragment = supportFragmentManager.findFragmentByTag(FRAGMENT_NO_ROM_DIRECTORIES) as NoRomSearchDirectoriesFragment?
        if (noRomDirectoriesFragment == null) {
            noRomDirectoriesFragment = NoRomSearchDirectoriesFragment.newInstance()
            supportFragmentManager.commit {
                replace(R.id.layout_main, noRomDirectoriesFragment, FRAGMENT_NO_ROM_DIRECTORIES)
            }
        }
    }

    private fun addRomListFragment() {
        var romListFragment = supportFragmentManager.findFragmentByTag(FRAGMENT_ROM_LIST) as RomListFragment?
        if (romListFragment == null) {
            romListFragment = RomListFragment.newInstance(true, RomListFragment.RomEnableCriteria.ENABLE_ALL)
            supportFragmentManager.commit {
                replace(R.id.layout_main, romListFragment, FRAGMENT_ROM_LIST)
            }
        }
        romListFragment.setRomSelectedListener { rom -> loadRom(rom) }
    }

    private fun showUpdateAvailableDialog(update: AppUpdate) {
        val message = markwon.toMarkdown(update.description)

        AlertDialog.Builder(this)
            .setTitle(getString(R.string.update_available, getReadableVersionString(update.newVersion)))
            .setMessage(message)
            .setPositiveButton(R.string.update) { _, _ ->
                startUpdateDownload(update)
            }
            .setNegativeButton(R.string.cancel, null)
            .setNeutralButton(R.string.skip_update) { _, _ ->
                updatesViewModel.skipUpdate(update)
            }
            .show()
    }

    private fun startUpdateDownload(update: AppUpdate) {
        downloadProgressDialog?.dismiss()
        downloadProgressDialog = AlertDialog.Builder(this)
            .setTitle(R.string.downloading_update)
            .setView(R.layout.dialog_layout_update_download_progress)
            .setPositiveButton(R.string.move_to_background) { _, _ ->
                downloadProgressDialog = null
            }
            .setCancelable(false)
            .show()

        updatesViewModel.downloadUpdate(update)
    }

    private fun onDownloadProgressUpdated(downloadProgress: DownloadProgress) {
        downloadProgressDialog?.let {
            when (downloadProgress) {
                is DownloadProgress.DownloadUpdate -> {
                    val progressBar = it.findViewById<ProgressBar>(R.id.progress_bar_download_progress)!!
                    val progressText = it.findViewById<TextView>(R.id.text_download_progress)!!

                    val progress = (downloadProgress.downloadedBytes.toDouble() / downloadProgress.totalSize) * 100
                    val downloadedMb = (downloadProgress.downloadedBytes.toDouble() / 1024 / 1024)
                    val totalMb = (downloadProgress.totalSize.toDouble() / 1024 / 1024)

                    progressBar.apply {
                        isIndeterminate = false
                        this.progress = progress.toInt()
                    }

                    progressText.text = getString(R.string.download_progress_sizes, downloadedMb, totalMb)
                }
                is DownloadProgress.DownloadComplete -> {
                    it.dismiss()
                    downloadProgressDialog = null
                }
                is DownloadProgress.DownloadFailed -> {
                    it.dismiss()
                    downloadProgressDialog = null
                    Toast.makeText(this, R.string.update_download_failed, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun getReadableVersionString(version: Version): String {
        val typeString = when(version.type) {
            Version.ReleaseType.ALPHA -> getString(R.string.version_alpha)
            Version.ReleaseType.BETA -> getString(R.string.version_beta)
            Version.ReleaseType.FINAL -> ""
        }
        return "$typeString${if (typeString.isEmpty()) "" else " "}${version.major}.${version.minor}.${version.patch}"
    }

    private fun loadRom(rom: Rom) {
        if (rom.isDsiWareTitle) {
            AlertDialog.Builder(this)
                .setTitle(R.string.dsiware_title_cannot_be_launched_directly_title)
                .setMessage(R.string.dsiware_title_cannot_be_launched_directly_message)
                .setPositiveButton(R.string.ok) { dialog, _ ->
                    dialog.dismiss()
                }
                .show()
            return
        }

        selectedRom = rom
        selectedFirmwareConsole = null

        val configurationDirResult = viewModel.getRomConfigurationDirStatus(rom)
        if (configurationDirResult.status == ConfigurationDirResult.Status.VALID) {
            val intent = EmulatorActivity.getRomEmulatorActivityIntent(this, rom)
            startActivity(intent)
        } else {
            showIncorrectConfigurationDirectoryDialog(configurationDirResult)
        }
    }

    private fun bootFirmware(consoleType: ConsoleType) {
        selectedRom = null
        selectedFirmwareConsole = consoleType

        val configurationDirResult = viewModel.getConsoleConfigurationDirResult(consoleType)
        if (configurationDirResult.status == ConfigurationDirResult.Status.VALID) {
            val intent = EmulatorActivity.getFirmwareEmulatorActivityIntent(this, consoleType)
            startActivity(intent)
        } else {
            showIncorrectConfigurationDirectoryDialog(configurationDirResult)
        }
    }

    private fun showIncorrectConfigurationDirectoryDialog(configurationDirResult: ConfigurationDirResult) {
        when (configurationDirResult.consoleType) {
            ConsoleType.DS -> {
                val titleRes: Int
                val messageRes: Int
                if (configurationDirResult.status == ConfigurationDirResult.Status.UNSET) {
                    titleRes = R.string.ds_bios_dir_not_set
                    messageRes = R.string.ds_bios_dir_not_set_info
                } else {
                    titleRes = R.string.incorrect_bios_dir
                    messageRes = R.string.ds_incorrect_bios_dir_info
                }

                AlertDialog.Builder(this)
                        .setTitle(titleRes)
                        .setMessage(messageRes)
                        .setPositiveButton(R.string.ok) { _, _ -> dsBiosPickerLauncher.launch(null) }
                        .setNegativeButton(R.string.cancel, null)
                        .setCancelable(true)
                        .show()
            }
            ConsoleType.DSi -> {
                val titleRes: Int
                val messageRes: Int
                if (configurationDirResult.status == ConfigurationDirResult.Status.UNSET) {
                    titleRes = R.string.dsi_bios_dir_not_set
                    messageRes = R.string.dsi_bios_dir_not_set_info
                } else {
                    titleRes = R.string.incorrect_bios_dir
                    messageRes = R.string.dsi_incorrect_bios_dir_info
                }

                AlertDialog.Builder(this)
                        .setTitle(titleRes)
                        .setMessage(messageRes)
                        .setPositiveButton(R.string.ok) { _, _ -> dsiBiosPickerLauncher.launch(null) }
                        .setNegativeButton(R.string.cancel, null)
                        .setCancelable(true)
                        .show()
            }
        }
    }

    private fun showInvalidDirectoryAccessDialog() {
        AlertDialog.Builder(this)
            .setTitle(R.string.error_invalid_directory)
            .setMessage(R.string.error_invalid_directory_description)
            .setPositiveButton(R.string.ok, null)
            .setCancelable(true)
            .show()
    }
}