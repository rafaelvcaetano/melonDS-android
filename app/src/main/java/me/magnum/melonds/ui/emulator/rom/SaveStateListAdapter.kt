package me.magnum.melonds.ui.emulator.rom

import android.database.DataSetObserver
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ListAdapter
import androidx.core.view.isGone
import androidx.core.view.isInvisible
import com.squareup.picasso.Callback
import com.squareup.picasso.Picasso
import me.magnum.melonds.R
import me.magnum.melonds.databinding.ItemSaveStateSlotBinding
import me.magnum.melonds.domain.model.SaveStateSlot
import java.text.SimpleDateFormat

class SaveStateListAdapter(
    slots: List<SaveStateSlot>,
    private val picasso: Picasso,
    private val dateFormat: SimpleDateFormat,
    private val timeFormat: SimpleDateFormat,
    private val onSlotSelected: (SaveStateSlot) -> Unit,
    private val onDeletedSlot: (SaveStateSlot) -> Unit,
) : ListAdapter {

    private val items = slots.toMutableList()
    private val observers = mutableListOf<DataSetObserver?>()

    fun updateSaveStateSlots(slots: List<SaveStateSlot>) {
        items.clear()
        items.addAll(slots)
        observers.forEach { it?.onChanged() }
    }

    override fun registerDataSetObserver(observer: DataSetObserver?) {
        observers.add(observer)
    }

    override fun unregisterDataSetObserver(observer: DataSetObserver?) {
        observers.remove(observer)
    }

    override fun getCount(): Int {
        return items.size
    }

    override fun getItem(position: Int): Any {
        return items[position]
    }

    override fun getItemId(position: Int): Long {
        return items[position].slot.toLong()
    }

    override fun hasStableIds(): Boolean {
        return true
    }

    override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View? {
        if (parent == null) {
            return null
        }

        val view = if (convertView == null) {
            ItemSaveStateSlotBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        } else {
            ItemSaveStateSlotBinding.bind(convertView)
        }

        val item = items[position]

        if (item.screenshot != null) {
            view.imageScreenshot.isGone = false
            picasso.load(item.screenshot).into(view.imageScreenshot, object : Callback {
                override fun onSuccess() {
                }

                override fun onError(e: Exception?) {
                    view.imageScreenshot.isGone = true
                }
            })
        } else {
            view.imageScreenshot.isGone = true
        }

        if (item.exists) {
            view.textDate.isGone = false
            view.textTime.isGone = false
            view.textSlot.text = parent.context.getString(R.string.save_state_slot, item.slot)
            view.textDate.text = dateFormat.format(item.lastUsedDate!!)
            view.textTime.text = timeFormat.format(item.lastUsedDate)
        } else {
            view.textDate.isGone = true
            view.textTime.isGone = true
            view.textSlot.text = parent.context.getString(R.string.empty_slot, item.slot)
        }

        view.root.setOnClickListener {
            onSlotSelected(item)
        }
        view.buttonDelete.setOnClickListener {
            onDeletedSlot(item)
        }
        view.buttonDelete.isInvisible = !item.exists
        view.divider.isGone = position == items.size - 1

        return view.root
    }

    override fun getItemViewType(position: Int): Int {
        return 0
    }

    override fun getViewTypeCount(): Int {
        return 1
    }

    override fun isEmpty(): Boolean {
        return items.isEmpty()
    }

    override fun areAllItemsEnabled(): Boolean {
        return true
    }

    override fun isEnabled(position: Int): Boolean {
        return true
    }
}