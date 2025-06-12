package com.tenacy.roadcapture.util

import androidx.recyclerview.widget.DiffUtil
import com.tenacy.roadcapture.ui.TimezoneItem

class DiffUtilCallback<out T : Any>(
    private val oldList: List<T>,
    private val newList: List<T>,
) : DiffUtil.Callback() {
    override fun getOldListSize(): Int = oldList.size

    override fun getNewListSize(): Int = newList.size

    override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
        val oldItem = oldList[oldItemPosition]
        val newItem = newList[newItemPosition]

        return when {
            oldItem is TimezoneItem && newItem is TimezoneItem -> oldItem.id == newItem.id
            else -> oldItem == newItem
        }
    }

    override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
        val oldItem = oldList[oldItemPosition]
        val newItem = newList[newItemPosition]

        return when {
            oldItem is TimezoneItem && newItem is TimezoneItem -> oldItem.value.isSelected == newItem.value.isSelected
            else -> true
        }
    }
}