package me.magnum.melonds.ui.romlist

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.android.synthetic.main.fragment_no_directories.*
import me.magnum.melonds.R
import me.magnum.melonds.utils.DirectoryPickerContract

@AndroidEntryPoint
class NoRomSearchDirectoriesFragment : Fragment() {
    companion object {
        fun newInstance(): NoRomSearchDirectoriesFragment {
            return NoRomSearchDirectoriesFragment()
        }
    }

    private val romListViewModel: RomListViewModel by activityViewModels()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_no_directories, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val romPickerLauncher = registerForActivityResult(DirectoryPickerContract()) {
            if (it != null)
                romListViewModel.addRomSearchDirectory(it)
        }

        buttonSetRomDirectory.setOnClickListener {
            romPickerLauncher.launch(null)
        }
    }
}