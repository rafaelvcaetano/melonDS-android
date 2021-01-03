package me.magnum.melonds.ui.romlist

import android.Manifest
import android.app.SearchManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import me.magnum.melonds.R
import me.magnum.melonds.ServiceLocator
import me.magnum.melonds.domain.model.Rom
import me.magnum.melonds.domain.model.SortingMode
import me.magnum.melonds.parcelables.RomParcelable
import me.magnum.melonds.domain.repositories.SettingsRepository
import me.magnum.melonds.ui.SettingsActivity
import me.magnum.melonds.ui.emulator.EmulatorActivity
import me.magnum.melonds.utils.ConfigurationUtils
import me.magnum.melonds.utils.DirectoryPickerContract

class RomListActivity : AppCompatActivity() {
    companion object {
        private const val REQUEST_STORAGE_PERMISSION = 1

        private const val FRAGMENT_ROM_LIST = "ROM_LIST"
        private const val FRAGMENT_NO_ROM_DIRECTORIES = "NO_ROM_DIRECTORY"
    }

    private val viewModel: RomListViewModel by viewModels { ServiceLocator[ViewModelProvider.Factory::class] }
    private val settingsRepository by lazy { ServiceLocator[SettingsRepository::class] }
    private val biosPickerLauncher by lazy { registerForActivityResult(DirectoryPickerContract()) {
        if (checkConfigDirectorySetup(it)) {
            settingsRepository.setBiosDirectory(it!!)
        }
    } }

    private var selectedRom: Rom? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_rom_list)
        viewModel.getRomScanningDirectories().observe(this, Observer {
            if (it.isEmpty())
                addNoSearchDirectoriesFragment()
            else
                addRomListFragment()
        })
    }

    override fun onStart() {
        super.onStart()
        checkConfigDirectorySetup()
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        if (menu == null)
            return super.onCreateOptionsMenu(menu)

        menuInflater.inflate(R.menu.rom_list_menu, menu)

        val searchItem =  menu.findItem(R.id.action_search_roms)
        ContextCompat.getSystemService(this, SearchManager::class.java)?.let { searchManager ->
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
            override fun onMenuItemActionExpand(item: MenuItem?): Boolean {
                return true
            }

            override fun onMenuItemActionCollapse(item: MenuItem?): Boolean {
                invalidateOptionsMenu()
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

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode != REQUEST_STORAGE_PERMISSION)
            return

        if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            selectedRom?.let {
                loadRom(it)
            }
        } else
            Toast.makeText(this, getString(R.string.info_no_storage_permission), Toast.LENGTH_LONG).show()
    }

    private fun checkConfigDirectorySetup(checkDirectory: Uri? = null): Boolean {
        val configDir = checkDirectory ?: settingsRepository.getBiosDirectory()
        when (ConfigurationUtils.checkConfigurationDirectory(this, configDir)) {
            ConfigurationUtils.ConfigurationDirStatus.VALID -> return true
            ConfigurationUtils.ConfigurationDirStatus.UNSET -> AlertDialog.Builder(this)
                    .setTitle(R.string.bios_dir_not_set)
                    .setMessage(R.string.bios_dir_not_set_info)
                    .setPositiveButton(R.string.ok) { _, _ -> biosPickerLauncher.launch(null) }
                    .setCancelable(false)
                    .show()
            ConfigurationUtils.ConfigurationDirStatus.INVALID -> AlertDialog.Builder(this)
                    .setTitle(R.string.incorrect_bios_dir)
                    .setMessage(R.string.incorrect_bios_dir_info)
                    .setPositiveButton(R.string.ok) { _, _ -> biosPickerLauncher.launch(null) }
                    .setCancelable(false)
                    .show()
        }
        return false
    }

    private fun isStoragePermissionGranted(): Boolean {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestStoragePermission(overrideRationaleRequest: Boolean) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M)
            return

        if (!overrideRationaleRequest && shouldShowRequestPermissionRationale(Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
            AlertDialog.Builder(this)
                    .setTitle(R.string.storage_permission_required)
                    .setMessage(R.string.storage_permission_required_info)
                    .setPositiveButton(R.string.ok) { _, _ -> requestStoragePermission(true) }
                    .show()
        } else {
            requestPermissions(arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE), REQUEST_STORAGE_PERMISSION)
        }
    }

    private fun addNoSearchDirectoriesFragment() {
        var noRomDirectoriesFragment = supportFragmentManager.findFragmentByTag(FRAGMENT_NO_ROM_DIRECTORIES) as NoRomSearchDirectoriesFragment?
        if (noRomDirectoriesFragment == null) {
            noRomDirectoriesFragment = NoRomSearchDirectoriesFragment.newInstance()
            supportFragmentManager.beginTransaction()
                    .replace(R.id.layout_main, noRomDirectoriesFragment, FRAGMENT_NO_ROM_DIRECTORIES)
                    .commit()
        }
    }

    private fun addRomListFragment() {
        var romListFragment = supportFragmentManager.findFragmentByTag(FRAGMENT_ROM_LIST) as RomListFragment?
        if (romListFragment == null) {
            romListFragment = RomListFragment.newInstance()
            supportFragmentManager.beginTransaction()
                    .replace(R.id.layout_main, romListFragment, FRAGMENT_ROM_LIST)
                    .commit()
        }
        romListFragment.setRomSelectedListener { rom -> loadRom(rom) }
    }

    private fun loadRom(rom: Rom) {
        if (isStoragePermissionGranted()) {
            val intent = Intent(this, EmulatorActivity::class.java)
            intent.putExtra(EmulatorActivity.KEY_ROM, RomParcelable(rom))
            startActivity(intent)
        } else {
            selectedRom = rom
            requestStoragePermission(false)
        }
    }
}