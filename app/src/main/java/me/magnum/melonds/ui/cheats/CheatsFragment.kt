package me.magnum.melonds.ui.cheats

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.commit
import androidx.fragment.app.replace
import dagger.hilt.android.AndroidEntryPoint
import me.magnum.melonds.R
import me.magnum.melonds.databinding.FragmentCheatsBinding

@AndroidEntryPoint
class CheatsFragment : Fragment() {
    companion object {
        private const val TAG_BACK_STACK_CHEATS = "back_stack_cheats"
    }

    private val viewModel: CheatsViewModel by activityViewModels()

    private var contentTitleChangeListener: ((String?) -> Unit)? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val binding = FragmentCheatsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        if (viewModel.getGames().size == 1) {
            viewModel.setSelectedGame(viewModel.getGames().first())
            childFragmentManager.commit {
                replace<FoldersSubScreenFragment>(R.id.layout_cheats_root)
                addToBackStack(TAG_BACK_STACK_CHEATS)
            }
        } else {
            childFragmentManager.commit {
                replace<GamesSubScreenFragment>(R.id.layout_cheats_root)
                addToBackStack(TAG_BACK_STACK_CHEATS)
            }

            // We only need to observe game changes if there's more than 1
            viewModel.getSelectedGame().observe(viewLifecycleOwner) {
                openGameFoldersFragment()
            }
        }

        childFragmentManager.addOnBackStackChangedListener {
            val fragment = childFragmentManager.fragments.last()
            val currentTitle = when (fragment) {
                is FoldersSubScreenFragment -> {
                    if (viewModel.getGames().size == 1) {
                        null
                    } else {
                        viewModel.getSelectedGame().value?.name
                    }
                }
                is CheatsSubScreenFragment -> viewModel.getSelectedFolder().value?.name
                else -> null
            }
            contentTitleChangeListener?.invoke(currentTitle)
        }

        viewModel.getSelectedFolder().observe(viewLifecycleOwner) {
            openCheatsFragment()
        }
    }

    private fun openGameFoldersFragment() {
        openSubScreenFragment<FoldersSubScreenFragment>()
    }

    private fun openCheatsFragment() {
        openSubScreenFragment<CheatsSubScreenFragment>()
    }

    private inline fun <reified T : Fragment> openSubScreenFragment() {
        childFragmentManager.commit {
            setCustomAnimations(R.anim.fragment_translate_enter_push, R.anim.fragment_translate_exit_push, R.anim.fragment_translate_enter_pop, R.anim.fragment_translate_exit_pop)
            replace<T>(R.id.layout_cheats_root)
            addToBackStack(TAG_BACK_STACK_CHEATS)
        }
    }

    fun setOnContentTitleChangedListener(listener: (String?) -> Unit) {
        contentTitleChangeListener = listener
    }
}