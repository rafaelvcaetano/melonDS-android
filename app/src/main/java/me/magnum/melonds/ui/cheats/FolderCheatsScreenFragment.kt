package me.magnum.melonds.ui.cheats

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isGone
import androidx.recyclerview.widget.RecyclerView
import me.magnum.melonds.databinding.ItemCheatsCheatBinding
import me.magnum.melonds.domain.model.Cheat
import me.magnum.melonds.extensions.setViewEnabledRecursive

class FolderCheatsScreenFragment : SubScreenFragment() {

    override fun getScreenName(): String? {
        return viewModel.selectedCheatFolder.value?.name
    }

    override fun getSubScreenAdapter(): RecyclerView.Adapter<*> {
        return CheatsAdapter(viewModel.getSelectedFolderCheats()) { cheat, isEnabled ->
            viewModel.notifyCheatEnabledStatusChanged(cheat, isEnabled)
        }
    }

    private class CheatsAdapter(private val cheats: List<Cheat>, private val onCheatEnableToggled: (Cheat, Boolean) -> Unit) : RecyclerView.Adapter<CheatsAdapter.ViewHolder>() {

        class ViewHolder(private val binding: ItemCheatsCheatBinding, private val onCheatEnableToggled: (Cheat, Boolean) -> Unit) : RecyclerView.ViewHolder(binding.root) {

            fun setCheat(cheat: Cheat) {
                val isCheatValid = cheat.isValid()
                binding.root.setViewEnabledRecursive(isCheatValid)
                binding.textCheatName.text = cheat.name
                binding.textCheatDescription.isGone = cheat.description.isNullOrEmpty()
                binding.textCheatDescription.text = cheat.description
                binding.checkboxCheatEnabled.isChecked = isCheatValid && cheat.enabled

                binding.checkboxCheatEnabled.setOnCheckedChangeListener { _, isEnabled ->
                    onCheatEnableToggled.invoke(cheat, isEnabled)
                }
            }

            fun toggle() {
                binding.checkboxCheatEnabled.toggle()
            }
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val binding = ItemCheatsCheatBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return ViewHolder(binding, onCheatEnableToggled).apply {
                itemView.setOnClickListener {
                    toggle()
                }
            }
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            holder.setCheat(cheats[position])
        }

        override fun getItemCount(): Int {
            return cheats.size
        }
    }
}