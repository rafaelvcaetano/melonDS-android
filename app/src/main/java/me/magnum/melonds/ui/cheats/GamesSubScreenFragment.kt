package me.magnum.melonds.ui.cheats

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import me.magnum.melonds.R
import me.magnum.melonds.databinding.ItemCheatsGameBinding
import me.magnum.melonds.domain.model.Game

class GamesSubScreenFragment : SubScreenFragment() {

    override fun getSubScreenAdapter(): RecyclerView.Adapter<*> {
        return GamesAdapter(viewModel.getGames()) {
            viewModel.setSelectedGame(it)
        }
    }

    override fun getScreenName(): String {
        return getString(R.string.cheats)
    }

    private class GamesAdapter(val games: List<Game>, private val onGameClicked: (Game) -> Unit) : RecyclerView.Adapter<GamesAdapter.ViewHolder>() {
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

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val binding = ItemCheatsGameBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return ViewHolder(binding).apply {
                itemView.setOnClickListener {
                    onGameClicked(getGame())
                }
            }
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            holder.setGame(games[position])
        }

        override fun getItemCount(): Int {
            return games.size
        }
    }
}