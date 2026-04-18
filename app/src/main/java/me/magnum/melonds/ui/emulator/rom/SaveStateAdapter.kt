package me.magnum.melonds.ui.emulator.rom

import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isGone
import androidx.core.view.isInvisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.squareup.picasso.Callback
import com.squareup.picasso.Picasso
import me.magnum.melonds.R
import me.magnum.melonds.databinding.ItemSaveStateSlotBinding
import me.magnum.melonds.domain.model.SaveStateSlot
import java.text.SimpleDateFormat

class SaveStateAdapter(
    slots: List<SaveStateSlot>,
    private val picasso: Picasso,
    private val dateFormat: SimpleDateFormat,
    private val timeFormat: SimpleDateFormat,
    private val onSlotSelected: (SaveStateSlot) -> Unit,
    private val onDeletedSlot: (SaveStateSlot) -> Unit,
) : RecyclerView.Adapter<SaveStateAdapter.ViewHolder>() {

    private val items = slots.toMutableList()

    fun updateSaveStateSlots(slots: List<SaveStateSlot>) {
        val differ = SaveSlotDiffer(items, slots)
        val diffResult = DiffUtil.calculateDiff(differ)

        items.clear()
        items.addAll(slots)
        diffResult.dispatchUpdatesTo(this)
    }

    override fun getItemId(position: Int): Long {
        return items[position].slot.toLong()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemSaveStateSlotBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding, onSlotSelected, onDeletedSlot)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int {
        return items.size
    }

    inner class ViewHolder(
        private val binding: ItemSaveStateSlotBinding,
        private val onSlotSelected: (SaveStateSlot) -> Unit,
        private val onDeletedSlot: (SaveStateSlot) -> Unit,
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: SaveStateSlot) {
            val context = binding.root.context

            // Screenshot
            if (item.screenshot != null) {
                binding.imageScreenshot.isGone = false
                picasso.load(item.screenshot).into(binding.imageScreenshot, object : Callback {
                    override fun onSuccess() {}
                    override fun onError(e: Exception?) {
                        binding.imageScreenshot.isGone = true
                    }
                })
            } else {
                binding.imageScreenshot.isGone = true
            }

            // Labels
            if (item.exists) {
                binding.textDate.isGone = false
                binding.textTime.isGone = false
                binding.textSlot.text = context.getString(R.string.save_state_slot, getSaveSateSlotName(context, item))
                binding.textDate.text = dateFormat.format(item.lastUsedDate!!)
                binding.textTime.text = timeFormat.format(item.lastUsedDate)
            } else {
                binding.textDate.isGone = true
                binding.textTime.isGone = true
                binding.textSlot.text = context.getString(R.string.empty_slot, getSaveSateSlotName(context, item))
            }

            binding.root.setOnClickListener {
                onSlotSelected(item)
            }

            binding.buttonDelete.apply {
                isInvisible = !item.exists
                setOnClickListener { onDeletedSlot(item) }
            }
        }

        private fun getSaveSateSlotName(context: Context, slot: SaveStateSlot): String {
            return if (slot.slot == SaveStateSlot.QUICK_SAVE_SLOT) {
                context.getString(R.string.quick_slot)
            } else {
                slot.slot.toString()
            }
        }
    }

    private class SaveSlotDiffer(private val oldSlots: List<SaveStateSlot>, private val newSlots: List<SaveStateSlot>) : DiffUtil.Callback() {

        override fun getOldListSize() = oldSlots.size

        override fun getNewListSize() = newSlots.size

        override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
            return oldSlots[oldItemPosition].slot == newSlots[newItemPosition].slot
        }

        override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
            return oldSlots[oldItemPosition] == newSlots[newItemPosition]
        }
    }
}