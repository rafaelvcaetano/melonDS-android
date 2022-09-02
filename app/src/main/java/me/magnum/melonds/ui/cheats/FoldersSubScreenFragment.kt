package me.magnum.melonds.ui.cheats

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import me.magnum.melonds.R
import me.magnum.melonds.databinding.ItemCheatsFolderBinding
import me.magnum.melonds.domain.model.CheatFolder

class FoldersSubScreenFragment : SubScreenFragment() {

    override fun getSubScreenAdapter(): RecyclerView.Adapter<*> {
        return FoldersAdapter(viewModel.getSelectedGame().value?.cheats ?: emptyList()) {
            viewModel.setSelectedFolder(it)
        }
    }

    override fun getScreenName(): String? {
        return if (viewModel.getGames().size == 1) {
            getString(R.string.cheats)
        } else {
            viewModel.getSelectedGame().value?.name
        }
    }

    private class FoldersAdapter(val folders: List<CheatFolder>, private val onFolderClicked: (CheatFolder) -> Unit) : RecyclerView.Adapter<FoldersAdapter.ViewHolder>() {
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
    }
}