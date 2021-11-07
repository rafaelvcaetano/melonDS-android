package me.magnum.melonds.ui.emulator.rewind

import android.content.Context
import android.graphics.drawable.BitmapDrawable
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.marginLeft
import androidx.core.view.marginRight
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import me.magnum.melonds.R
import me.magnum.melonds.databinding.ItemRewindSaveStateBinding
import me.magnum.melonds.ui.emulator.rewind.model.RewindSaveState
import me.magnum.melonds.ui.emulator.rewind.model.RewindWindow
import java.text.DecimalFormat
import java.time.Duration

class RewindSaveStateAdapter(private val onRewindSaveStateSelected: (RewindSaveState) -> Unit) : RecyclerView.Adapter<RewindSaveStateAdapter.RewindSaveStateViewHolder>() {

    class RewindSaveStateViewHolder(private val context: Context, private val binding: ItemRewindSaveStateBinding) : RecyclerView.ViewHolder(binding.root) {

        companion object {
            private val SECONDS_FORMATTER = DecimalFormat("#0.##")
        }

        private lateinit var state: RewindSaveState

        fun setRewindSaveState(state: RewindSaveState, window: RewindWindow) {
            val screenshotDrawable = BitmapDrawable(context.resources, state.screenshot)
            val durationToState = window.getDeltaFromEmulationTimeToRewindState(state)

            binding.imageScreenshot.setImageDrawable(screenshotDrawable)
            binding.textTimestamp.text = getDurationString(context, durationToState)
            this.state = state
        }

        fun getRewindSaveState(): RewindSaveState {
            return state
        }

        private fun getDurationString(context: Context, duration: Duration): String {
            val minutes = duration.toMinutes()
            return if (minutes >= 1) {
                val seconds = duration.minusMinutes(minutes).toMillis() / 1000f
                context.getString(R.string.rewind_time_minutes_seconds, minutes, SECONDS_FORMATTER.format(seconds))
            } else {
                val seconds = duration.toMillis() / 1000f
                context.getString(R.string.rewind_time_seconds, SECONDS_FORMATTER.format(seconds))
            }
        }
    }

    private var currentRewindWindow: RewindWindow? = null
    private var recyclerView: RecyclerView? = null

    fun setRewindWindow(rewindWindow: RewindWindow) {
        currentRewindWindow = rewindWindow
        notifyDataSetChanged()
        // Reset scroll
        recyclerView?.scrollToPosition(0)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RewindSaveStateViewHolder {
        val binding = ItemRewindSaveStateBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        val holder = RewindSaveStateViewHolder(parent.context, binding)
        binding.root.setOnClickListener {
            onRewindSaveStateSelected(holder.getRewindSaveState())
        }
        binding.root.setOnFocusChangeListener { v, hasFocus ->
            if (hasFocus) {
                recyclerView?.let {
                    // Scroll to new focused item (allows controller users to properly navigate the list)
                    val linearLayoutManager = it.layoutManager as? LinearLayoutManager ?: return@let
                    val position = linearLayoutManager.getPosition(v)
                    val offset = (it.width - v.width - v.marginRight - v.marginLeft) / 2
                    linearLayoutManager.scrollToPositionWithOffset(position, offset)
                }
            }
        }
        return holder
    }

    override fun onAttachedToRecyclerView(recyclerView: RecyclerView) {
        this.recyclerView = recyclerView
    }

    override fun onBindViewHolder(holder: RewindSaveStateViewHolder, position: Int) {
        currentRewindWindow?.let {
            val state = it.rewindStates[position]
            holder.setRewindSaveState(state, it)
        }
    }

    override fun getItemCount(): Int {
        return currentRewindWindow?.rewindStates?.size ?: 0
    }
}