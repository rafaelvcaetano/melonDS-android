package me.magnum.melonds.ui.romlist

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import dagger.hilt.android.AndroidEntryPoint
import me.magnum.melonds.common.Permission
import me.magnum.melonds.common.contracts.DirectoryPickerContract
import me.magnum.melonds.databinding.FragmentNoDirectoriesBinding

@AndroidEntryPoint
class NoRomSearchDirectoriesFragment : Fragment() {
    companion object {
        fun newInstance(): NoRomSearchDirectoriesFragment {
            return NoRomSearchDirectoriesFragment()
        }
    }

    private lateinit var binding: FragmentNoDirectoriesBinding
    private val romListViewModel: RomListViewModel by activityViewModels()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = FragmentNoDirectoriesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val romPickerLauncher = registerForActivityResult(DirectoryPickerContract(Permission.READ_WRITE)) {
            if (it != null) {
                romListViewModel.addRomSearchDirectory(it)
            }
        }

        binding.buttonSetRomDirectory.setOnClickListener {
            romPickerLauncher.launch(null)
        }
    }
}