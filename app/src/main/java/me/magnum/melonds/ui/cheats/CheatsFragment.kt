package me.magnum.melonds.ui.cheats

import android.os.Bundle
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import androidx.core.view.MenuProvider
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.commit
import androidx.fragment.app.replace
import dagger.hilt.android.AndroidEntryPoint
import me.magnum.melonds.R
import me.magnum.melonds.databinding.FragmentCheatsBinding
import me.magnum.melonds.extensions.viewBinding

@AndroidEntryPoint
class CheatsFragment : Fragment(R.layout.fragment_cheats) {
    companion object {
        private const val TAG_BACK_STACK_CHEATS = "back_stack_cheats"
    }

    private val viewModel: CheatsViewModel by activityViewModels()
    private val binding by viewBinding(FragmentCheatsBinding::bind)

    private var isLaunchingForFirstTime = true

    private val cheatsMenuProvider = object : MenuProvider {
        override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
            menuInflater.inflate(R.menu.cheats_menu, menu)
        }

        override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
            when (menuItem.itemId) {
                R.id.action_cheats_filter_enabled -> viewModel.openEnabledCheats()
                else -> return false
            }
            return true
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        isLaunchingForFirstTime = savedInstanceState == null
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        requireActivity().addMenuProvider(cheatsMenuProvider, viewLifecycleOwner)

        if (isLaunchingForFirstTime) {
            val hasMultipleGames = (viewModel.getRomCheats().value?.size ?: 0) > 1
            if (hasMultipleGames) {
                openSubScreenFragment<GamesSubScreenFragment>(isRootFragment = true)
            } else {
                openSubScreenFragment<FoldersSubScreenFragment>(isRootFragment = true)
            }
        }

        viewModel.openFoldersEvent.observe(viewLifecycleOwner) {
            openSubScreenFragment<FoldersSubScreenFragment>()
        }

        viewModel.openCheatsEvent.observe(viewLifecycleOwner) {
            openSubScreenFragment<FolderCheatsScreenFragment>()
        }

        isLaunchingForFirstTime = false
    }

    private inline fun <reified T : Fragment> openSubScreenFragment(isRootFragment: Boolean = false) {
        childFragmentManager.commit {
            if (!isRootFragment) {
                setCustomAnimations(
                    R.anim.fragment_translate_enter_push,
                    R.anim.fragment_translate_exit_push,
                    R.anim.fragment_translate_enter_pop,
                    R.anim.fragment_translate_exit_pop
                )

                addToBackStack(TAG_BACK_STACK_CHEATS)
            }
            replace<T>(binding.layoutCheatsRoot.id)
        }
    }
}