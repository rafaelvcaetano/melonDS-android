package me.magnum.melonds.ui.cheats

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import me.magnum.melonds.R
import me.magnum.melonds.databinding.ItemCheatsFolderBinding
import me.magnum.melonds.domain.model.CheatFolder
import me.magnum.melonds.ui.cheats.model.CheatsScreenUiState
import me.magnum.melonds.utils.SimpleDiffCallback

class FoldersSubScreenFragment : SubScreenFragment() {

    override fun getSubScreenAdapter(): RecyclerView.Adapter<*> {
        val adapter = FoldersAdapter {
            viewModel.setSelectedFolder(it)
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.selectedGameCheats.collectLatest {
                    updateScreenState(it)
                    when (it) {
                        is CheatsScreenUiState.Loading -> { }
                        is CheatsScreenUiState.Ready<*> -> adapter.updateCheatFolders(it.data as List<CheatFolder>)
                    }
                }
            }
        }

        return adapter
    }

    override fun getScreenName(): String {
        return viewModel.selectedGame.value?.name ?: getString(R.string.cheats)
    }

    private class FoldersAdapter(private val onFolderClicked: (CheatFolder) -> Unit) : RecyclerView.Adapter<FoldersAdapter.ViewHolder>() {
        class ViewHolder(private val binding: ItemCheatsFolderBinding) : RecyclerView.ViewHolder(binding.root) {
            private lateinit var folder: CheatFolder

            fun getFolder(): CheatFolder {
                return folder
            }

            fun setFolder(folder: CheatFolder) {
                this.folder = folder

                binding.textFolderName.text = folder.name
            }
        }

        private val folders = mutableListOf<CheatFolder>()

        fun updateCheatFolders(newCheatFolders: List<CheatFolder>) {
            val diffResult = DiffUtil.calculateDiff(FoldersDiffCallback(folders, newCheatFolders))
            diffResult.dispatchUpdatesTo(this)
            folders.apply {
                clear()
                addAll(newCheatFolders)
            }
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val binding = ItemCheatsFolderBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return ViewHolder(binding).apply {
                itemView.setOnClickListener {
                    onFolderClicked(getFolder())
                }
            }
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            holder.setFolder(folders[position])
        }

        override fun getItemCount(): Int {
            return folders.size
        }

        class FoldersDiffCallback(oldList: List<CheatFolder>, newList: List<CheatFolder>): SimpleDiffCallback<CheatFolder>(oldList, newList) {
            override fun areItemsTheSame(old: CheatFolder, new: CheatFolder): Boolean {
                return old.id == new.id
            }
        }
    }
}