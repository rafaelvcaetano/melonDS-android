package me.magnum.melonds.ui.cheats

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isGone
import androidx.recyclerview.widget.RecyclerView
import me.magnum.melonds.R
import me.magnum.melonds.databinding.ItemCheatsEnabledCheatBinding
import me.magnum.melonds.domain.model.Cheat
import me.magnum.melonds.domain.model.CheatInFolder
import me.magnum.melonds.extensions.setViewEnabledRecursive

class EnabledCheatsFragment : SubScreenFragment() {

    override fun getScreenName(): String {
        return getString(R.string.enabled_cheats)
    }

    override fun getNoContentText(): String {
        return getString(R.string.no_enabled_cheats_for_rom)
    }

    override fun getSubScreenAdapter(): RecyclerView.Adapter<*> {
        return EnabledCheatsAdapter(viewModel.getGameSelectedCheats()) { cheat, isEnabled ->
            viewModel.notifyCheatEnabledStatusChanged(cheat, isEnabled)
        }
    }

    private class EnabledCheatsAdapter(private val cheats: List<CheatInFolder>, private val onCheatEnableToggled: (Cheat, Boolean) -> Unit) : RecyclerView.Adapter<EnabledCheatsAdapter.ViewHolder>() {

        class ViewHolder(private val binding: ItemCheatsEnabledCheatBinding, private val onCheatEnableToggled: (Cheat, Boolean) -> Unit) : RecyclerView.ViewHolder(binding.root) {

            fun setCheat(cheatInFolder: CheatInFolder) {
                val cheat = cheatInFolder.cheat
                val isCheatValid = cheat.isValid()
                binding.root.setViewEnabledRecursive(isCheatValid)
                binding.textFolderName.text = cheatInFolder.folderName
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
            val binding = ItemCheatsEnabledCheatBinding.inflate(LayoutInflater.from(parent.context), parent, false)
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