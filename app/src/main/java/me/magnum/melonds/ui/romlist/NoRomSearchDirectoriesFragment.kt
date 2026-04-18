package me.magnum.melonds.ui.romlist

import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import dagger.hilt.android.AndroidEntryPoint
import me.magnum.melonds.R
import me.magnum.melonds.common.Permission
import me.magnum.melonds.common.contracts.DirectoryPickerContract
import me.magnum.melonds.databinding.FragmentNoDirectoriesBinding

@AndroidEntryPoint
class NoRomSearchDirectoriesFragment : Fragment() {
    companion object {
        fun newInstance(): NoRomSearchDirectoriesFragment {
            return NoRomSearchDirectoriesFragment()
        }

        private val DOCUMENT_PICKER_PACKAGES = listOf(
            "com.google.android.documentsui",
            "com.android.documentsui",
        )
    }

    private lateinit var binding: FragmentNoDirectoriesBinding
    private val romListViewModel: RomListViewModel by activityViewModels()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = FragmentNoDirectoriesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val packageManager = requireActivity().packageManager
        val directoryPickerContract = DirectoryPickerContract(Permission.READ_WRITE)
        val directoryPickerIntent = directoryPickerContract.createIntent(requireContext(), null)
        val directoryPickerComponent = packageManager.resolveActivity(directoryPickerIntent, PackageManager.MATCH_DEFAULT_ONLY)

        if (directoryPickerComponent == null) {
            val disabledFilePicker = findDisabledFilePicker()
            if (disabledFilePicker != null) {
                binding.textRomSearchDirectoryInfo.text = getString(R.string.system_file_picker_not_enabled)
                binding.buttonAction.apply {
                    text = getString(R.string.file_picker_settings)
                    visibility = View.VISIBLE
                    setOnClickListener {
                        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                        intent.data = Uri.fromParts("package", disabledFilePicker.packageName, null)
                        startActivity(intent)
                    }
                }
            } else {
                binding.textRomSearchDirectoryInfo.text = getString(R.string.system_file_picker_not_found)
                binding.buttonAction.visibility = View.GONE
            }
        } else {
            val romPickerLauncher = registerForActivityResult(directoryPickerContract) {
                if (it != null) {
                    romListViewModel.addRomSearchDirectory(it)
                }
            }

            binding.textRomSearchDirectoryInfo.text = getString(R.string.no_rom_search_directory_specified)
            binding.buttonAction.apply {
                text = getString(R.string.set_rom_directory)
                visibility = View.VISIBLE
                setOnClickListener {
                    romPickerLauncher.launch(null)
                }
            }
        }
    }

    private fun findDisabledFilePicker(): ApplicationInfo? {
        DOCUMENT_PICKER_PACKAGES.forEach {
            try {
                val appInfo = requireActivity().packageManager.getApplicationInfo(it, 0)
                if (!appInfo.enabled) {
                    return appInfo
                }
            } catch (e: PackageManager.NameNotFoundException) {
                // Ignore
            }
        }
        return null
    }
}