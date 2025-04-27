package com.tenacy.roadcapture.ui

import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.tenacy.roadcapture.util.DiffUtilCallback

abstract class BaseMVHRecyclerView<T: Any, VH: RecyclerView.ViewHolder>: RecyclerView.Adapter<VH>() {

    val items = arrayListOf<T>()

    fun updateItems(items: List<T>?) {
        items?.let {
            val diffCallback = DiffUtilCallback(this.items, items)
            val diffResult = DiffUtil.calculateDiff(diffCallback)

            this.items.run {
                clear()
                addAll(items)
                diffResult.dispatchUpdatesTo(this@BaseMVHRecyclerView)
            }
        }
    }

    override fun getItemCount(): Int = items.size
}