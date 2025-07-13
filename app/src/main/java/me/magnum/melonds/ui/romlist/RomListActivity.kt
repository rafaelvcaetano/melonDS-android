package me.magnum.melonds.ui.romlist

import android.app.SearchManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.Display
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
import me.magnum.melonds.domain.model.ConfigurationDirResult
import me.magnum.melonds.domain.model.ConsoleType
import me.magnum.melonds.domain.model.DownloadProgress
import me.magnum.melonds.domain.model.rom.Rom
import me.magnum.melonds.domain.model.SortingMode
import me.magnum.melonds.domain.model.Version
import me.magnum.melonds.domain.model.appupdate.AppUpdate
import me.magnum.melonds.ui.ExternalDisplayManager
import me.magnum.melonds.ui.ExternalPresentation
import me.magnum.melonds.ui.dsiwaremanager.DSiWareManagerActivity
import me.magnum.melonds.ui.emulator.EmulatorActivity
import me.magnum.melonds.ui.settings.SettingsActivity
import javax.inject.Inject
import androidx.core.graphics.toColorInt
import android.hardware.display.DisplayManager
import android.util.Log

@AndroidEntryPoint
class RomListActivity : AppCompatActivity() {

    /**
     * The [ExternalPresentation] instance used to display content on an external display, if available.
     * This allows the application to show a different UI or content on a secondary screen
     * connected to the device. It is initialized when an external display is detected and
     * dismissed when the activity is destroyed or the display is no longer available.
     */
    private var presentation: ExternalPresentation? = null
    /**
     * ViewModel responsible for managing and providing data related to RetroAchievements
     * for the ROM list. This ViewModel handles fetching achievement data,
     * user login status, and other RetroAchievements-specific information.
     * It is used by the [RomListActivity] to display achievements on an external
     * display when a ROM is focused.
     */
    private val achievementsViewModel: RomListRetroAchievementsViewModel by viewModels()

    /**
     * Repository for accessing and modifying application settings.
     * This is injected by Hilt and provides methods to retrieve and store
     * various user preferences and application configurations.
     */
    @Inject lateinit var settingsRepository: me.magnum.melonds.domain.repositories.SettingsRepository

    private lateinit var displayManager: DisplayManager

    private val displayListener = object : DisplayManager.DisplayListener {
        override fun onDisplayAdded(displayId: Int) {
            showExternalDisplay()
        }

        override fun onDisplayRemoved(displayId: Int) {
            if (presentation?.display?.displayId == displayId) {
                presentation?.dismiss()
                presentation = null
                ExternalDisplayManager.presentation = null
            }
        }

        override fun onDisplayChanged(displayId: Int) {
            // No-op
        }
    }

    override fun onAttachFragment(fragment: androidx.fragment.app.Fragment) {
        super.onAttachFragment(fragment)
        if (fragment is RomListFragment) {
            fragment.setRomFocusListener { onRomFocused(it) }
        }
    }

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

        displayManager = getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
        displayManager.registerDisplayListener(displayListener, null)
        showExternalDisplay()

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


    /**
     * Called when the activity is being destroyed.
     * This method dismisses the external presentation, unregisters the display listener,
     * and cleans up resources related to the external display.
     */
    override fun onDestroy() {
        super.onDestroy()
        presentation?.dismiss()
        displayManager.unregisterDisplayListener(displayListener)
        ExternalDisplayManager.presentation = null
    }


    /**
     * Attempts to show a presentation on an external display if one is available and
     * not already showing.
     *
     * This function first checks if a presentation is already active. If so, it returns
     * immediately. Otherwise, it queries the [DisplayManager] for available displays.
     * It logs the number and details of all found displays.
     *
     * It then specifically looks for displays in the [DisplayManager.DISPLAY_CATEGORY_PRESENTATION]
     * category, excluding the default built-in screen. If a suitable external display is found,
     * it initializes an [ExternalPresentation] with a black background and attempts to show it.
     *
     * Logging is performed at various stages to track the process and any potential errors.
     * If an external display is successfully utilized, the [presentation] field is updated,
     * and the [ExternalDisplayManager.presentation] is also set.
     * If no suitable external display is found, a warning is logged.
     */
    private fun showExternalDisplay() {
        if (presentation != null){
            return
        }

        val displays = displayManager.displays
        Log.d("DualScreen", "Found ${displays.size} displays.")
        for (display in displays) {
            Log.d("DualScreen", "Display ID: ${display.displayId}, Name: ${display.name}")
        }

        val external = displayManager.getDisplays(DisplayManager.DISPLAY_CATEGORY_PRESENTATION)
            .firstOrNull { it.displayId != Display.DEFAULT_DISPLAY && it.name != "Built-in Screen" }

        if (external != null) {
            Log.d("DualScreen", "Using external display: ID=${external.displayId}, Name=${external.name}")
            presentation = ExternalPresentation(this, external).apply {
                setBackground("black".toColorInt())

                setOnShowListener {
                    Log.d("DualScreen", "Presentation successfully shown on external display.")
                }

                try {
                    show()
                    Log.d("DualScreen", "Presentation.show() called")
                } catch (e: Exception) {
                    Log.e("DualScreen", "Error showing presentation: ${e.message}", e)
                }
            }
            ExternalDisplayManager.presentation = presentation
        } else {
            Log.w("DualScreen", "No external display found.")
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
        showExternalDisplay()
    }

    /**
     * Handles the event when a ROM is focused in the list.
     * This function ensures the external display is active, sets the focused ROM in the
     * [achievementsViewModel], and then instructs the [presentation] (if available)
     * to show achievements for that ROM, taking into account the current theme (dark/light).
     *
     * @param rom The [Rom] that has gained focus.
     */
    private fun onRomFocused(rom: Rom) {
        showExternalDisplay()
        achievementsViewModel.setRom(rom)
        presentation?.showAchievements(achievementsViewModel, isDarkTheme())
    }

    /**
     * Determines whether the current theme is dark.
     *
     * This function checks the theme setting stored in [settingsRepository].
     * If the theme is explicitly set to [me.magnum.melonds.ui.Theme.DARK], it returns `true`.
     * If the theme is explicitly set to [me.magnum.melonds.ui.Theme.LIGHT], it returns `false`.
     * If the theme setting is not explicitly set (e.g., system default), it falls back to checking
     * the system's current UI mode. It returns `true` if the system is in night mode
     * ([android.content.res.Configuration.UI_MODE_NIGHT_YES]), and `false` otherwise.
     *
     * @return `true` if the current theme is dark, `false` otherwise.
     */
    private fun isDarkTheme(): Boolean {
        val theme = settingsRepository.getTheme()
        return when (theme) {
            me.magnum.melonds.ui.Theme.DARK -> true
            me.magnum.melonds.ui.Theme.LIGHT -> false
            else -> (resources.configuration.uiMode and
                    android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
                    android.content.res.Configuration.UI_MODE_NIGHT_YES
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