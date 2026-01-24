package me.magnum.melonds.ui.romlist

import android.app.SearchManager
import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import androidx.core.content.getSystemService
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePadding
import androidx.fragment.app.commit
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import dagger.hilt.android.AndroidEntryPoint
import io.noties.markwon.Markwon
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import me.magnum.melonds.R
import me.magnum.melonds.databinding.ActivityRomListBinding
import me.magnum.melonds.domain.model.ConsoleType
import me.magnum.melonds.domain.model.DownloadProgress
import me.magnum.melonds.domain.model.SortingMode
import me.magnum.melonds.domain.model.Version
import me.magnum.melonds.domain.model.appupdate.AppUpdate
import me.magnum.melonds.domain.model.rom.Rom
import me.magnum.melonds.ui.common.rom.EmulatorLaunchValidatorDelegate
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
    private lateinit var emulatorLauncherValidatorDelegate: EmulatorLaunchValidatorDelegate

    private var downloadProgressDialog: AlertDialog? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        WindowCompat.enableEdgeToEdge(window)
        val binding = ActivityRomListBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)

        var defaultContentInsetLeft = -1
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, windowInsets ->
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            if (defaultContentInsetLeft == -1) {
                defaultContentInsetLeft = binding.toolbar.contentInsetLeft
            }

            binding.toolbar.setContentInsetsAbsolute(defaultContentInsetLeft + insets.left, binding.toolbar.contentInsetRight)
            binding.toolbar.updatePadding(
                left = insets.left,
                right = insets.right,
            )
            binding.viewStatusBarBackground.updateLayoutParams {
                height = insets.top
            }
            binding.layoutMain.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                leftMargin = insets.left
                rightMargin = insets.right
            }

            windowInsets.inset(insets.left, insets.top, insets.right, 0)
        }

        emulatorLauncherValidatorDelegate = EmulatorLaunchValidatorDelegate(this, object : EmulatorLaunchValidatorDelegate.Callback {
            override fun onRomValidated(rom: Rom) {
                val intent = EmulatorActivity.getRomEmulatorActivityIntent(this@RomListActivity, rom)
                startActivity(intent)
            }

            override fun onFirmwareValidated(consoleType: ConsoleType) {
                val intent = EmulatorActivity.getFirmwareEmulatorActivityIntent(this@RomListActivity, consoleType)
                startActivity(intent)
            }

            override fun onValidationAborted() {
                // Do nothing
            }
        })

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

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                updatesViewModel.appUpdate.collectLatest {
                    when (it.type) {
                        AppUpdate.Type.PRODUCTION -> showProdUpdateAvailableDialog(it)
                        AppUpdate.Type.NIGHTLY -> showNightlyUpdateAvailableDialog(it)
                    }
                }
            }
        }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                updatesViewModel.updateDownloadProgressEvent.collectLatest {
                    onDownloadProgressUpdated(it)
                }
            }
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
                launchFirmware(ConsoleType.DS)
                return true
            }
            R.id.action_boot_firmware_dsi -> {
                launchFirmware(ConsoleType.DSi)
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
        romListFragment.setRomSelectedListener { rom -> launchRom(rom) }
    }

    private fun showProdUpdateAvailableDialog(update: AppUpdate) {
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

    private fun showNightlyUpdateAvailableDialog(update: AppUpdate) {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.nightly_update_available))
            .setMessage(getString(R.string.nightly_update_available_message))
            .setPositiveButton(R.string.update) { _, _ ->
                startUpdateDownload(update)
            }
            .setNegativeButton(R.string.remind_later_update) { _, _ ->
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
            Version.ReleaseType.NIGHTLY -> return getString(R.string.version_nightly)
        }
        return "$typeString${if (typeString.isEmpty()) "" else " "}${version.major}.${version.minor}.${version.patch}"
    }

    private fun launchRom(rom: Rom) {
        emulatorLauncherValidatorDelegate.validateRom(rom)
    }

    private fun launchFirmware(consoleType: ConsoleType) {
        emulatorLauncherValidatorDelegate.validateFirmware(consoleType)
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