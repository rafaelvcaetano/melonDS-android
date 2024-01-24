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
import me.magnum.melonds.databinding.ItemCheatsGameBinding
import me.magnum.melonds.domain.model.Game
import me.magnum.melonds.ui.cheats.model.CheatsScreenUiState

class GamesSubScreenFragment : SubScreenFragment() {

    override fun getSubScreenAdapter(): RecyclerView.Adapter<*> {
        val adapter = GamesAdapter {
            viewModel.setSelectedGame(it)
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.games.collectLatest {
                    updateScreenState(it)
                    when (it) {
                        is CheatsScreenUiState.Loading -> { }
                        is CheatsScreenUiState.Ready<*> -> adapter.updateGames(it.data as List<Game>)
                    }
                }
            }
        }

        return adapter
    }

    override fun getScreenName(): String {
        return getString(R.string.cheats)
    }

    private class GamesAdapter(private val onGameClicked: (Game) -> Unit) : RecyclerView.Adapter<GamesAdapter.ViewHolder>() {
        class ViewHolder(private val binding: ItemCheatsGameBinding) : RecyclerView.ViewHolder(binding.root) {
            private lateinit var game: Game

            fun getGame(): Game {
                return game
            }

            fun setGame(game: Game) {
                this.game = game

                binding.textGameName.text = game.name
            }
        }

        private var games = emptyList<Game>()

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val binding = ItemCheatsGameBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return ViewHolder(binding).apply {
                itemView.setOnClickListener {
                    onGameClicked(getGame())
                }
            }
        }

        fun updateGames(newGames: List<Game>) {
            val result = DiffUtil.calculateDiff(GamesDillCallback(games, newGames))
            result.dispatchUpdatesTo(this)
            games = newGames
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            holder.setGame(games[position])
        }

        override fun getItemCount(): Int {
            return games.size
        }

        private class GamesDillCallback(val oldGames: List<Game>, val newGames: List<Game>) : DiffUtil.Callback() {
            override fun getOldListSize() = oldGames.size

            override fun getNewListSize() = newGames.size

            override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
                return oldGames[oldItemPosition].id == newGames[newItemPosition].id
            }

            override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
                return oldGames[oldItemPosition] == newGames[newItemPosition]
            }
        }
    }
}