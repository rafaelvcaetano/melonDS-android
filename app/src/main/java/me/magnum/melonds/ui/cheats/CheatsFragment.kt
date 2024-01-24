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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
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

        if (isLaunchingForFirstTime || !viewModel.initialContentReady.value) {
            viewLifecycleOwner.lifecycleScope.launch {
                viewModel.initialContentReady.collectLatest { ready ->
                    if (ready) {
                        val hasSelectedGame = viewModel.selectedGame.value != null
                        if (hasSelectedGame) {
                            openSubScreenFragment<FoldersSubScreenFragment>(isRootFragment = true)
                        } else {
                            openSubScreenFragment<GamesSubScreenFragment>(isRootFragment = true)
                        }
                    }
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.openFoldersEvent.collectLatest {
                    openSubScreenFragment<FoldersSubScreenFragment>()
                }
            }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.openCheatsEvent.collectLatest {
                    openSubScreenFragment<FolderCheatsScreenFragment>()
                }
            }
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