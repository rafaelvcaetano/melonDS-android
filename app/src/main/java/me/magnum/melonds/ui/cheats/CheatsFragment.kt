package me.magnum.melonds.ui.cheats

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
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
            childFragmentManager.beginTransaction()
                    .replace(R.id.layout_cheats_root, FoldersSubScreenFragment())
                    .addToBackStack(TAG_BACK_STACK_CHEATS)
                    .commit()
        } else {
            childFragmentManager.beginTransaction()
                    .replace(R.id.layout_cheats_root, GamesSubScreenFragment())
                    .addToBackStack(TAG_BACK_STACK_CHEATS)
                    .commit()

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
        childFragmentManager.beginTransaction()
                .setCustomAnimations(R.anim.fragment_translate_enter_push, R.anim.fragment_translate_exit_push, R.anim.fragment_translate_enter_pop, R.anim.fragment_translate_exit_pop)
                .replace(R.id.layout_cheats_root, FoldersSubScreenFragment())
                .addToBackStack(TAG_BACK_STACK_CHEATS)
                .commit()
    }

    private fun openCheatsFragment() {
        childFragmentManager.beginTransaction()
                .setCustomAnimations(R.anim.fragment_translate_enter_push, R.anim.fragment_translate_exit_push, R.anim.fragment_translate_enter_pop, R.anim.fragment_translate_exit_pop)
                .replace(R.id.layout_cheats_root, CheatsSubScreenFragment())
                .addToBackStack(TAG_BACK_STACK_CHEATS)
                .commit()
    }

    fun setOnContentTitleChangedListener(listener: (String?) -> Unit) {
        contentTitleChangeListener = listener
    }
}