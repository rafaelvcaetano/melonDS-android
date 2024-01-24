package me.magnum.melonds.utils

import androidx.recyclerview.widget.DiffUtil

abstract class SimpleDiffCallback<T>(private val oldList: List<T>, private val newList: List<T>) : DiffUtil.Callback() {
    override fun getOldListSize() = oldList.size

    override fun getNewListSize() = newList.size

    override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
        return areItemsTheSame(oldList[oldItemPosition], newList[newItemPosition])
    }

    override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
        return oldList[oldItemPosition] == newList[newItemPosition]
    }

    abstract fun areItemsTheSame(old: T, new: T): Boolean
}