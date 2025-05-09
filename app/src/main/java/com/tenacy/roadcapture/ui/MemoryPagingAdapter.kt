package com.tenacy.roadcapture.ui

import android.os.Parcelable
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.paging.PagingDataAdapter
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.tenacy.roadcapture.databinding.ItemMemoryBinding
import com.tenacy.roadcapture.ui.dto.Memory
import kotlinx.parcelize.Parcelize

@Parcelize
data class MemoryItem(
    val value: Memory,
    val onItemClick: (String) -> Unit,
): Parcelable

class MemoryViewHolder(private val binding: ItemMemoryBinding): RecyclerView.ViewHolder(binding.root) {

    fun bind(item: MemoryItem) {
        binding.photoUrl = item.value.photoUrl
    }

    fun bind(item: MemoryItem, payloads: List<Any>) {
        if (payloads.isEmpty()) {
            bind(item)
            return
        }

        payloads.forEach { payload ->
            if (payload is List<*>) {
                payload.forEach { change ->
                    when (change) {
                        "photoUrl" -> binding.photoUrl = item.value.photoUrl
                    }
                }
            }
        }
    }
}

class MemoryPagingAdapter: PagingDataAdapter<MemoryItem, MemoryViewHolder>(MemoryComparator) {

    private var recyclerView: RecyclerView? = null

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MemoryViewHolder {
        return MemoryViewHolder(ItemMemoryBinding.inflate(LayoutInflater.from(parent.context), parent, false))
    }

    override fun onBindViewHolder(holder: MemoryViewHolder, position: Int) {
        getItem(position)?.let(holder::bind)
    }

    override fun onBindViewHolder(holder: MemoryViewHolder, position: Int, payloads: MutableList<Any>) {
        if (payloads.isEmpty()) {
            super.onBindViewHolder(holder, position, payloads)
        } else {
            val item = getItem(position)
            if (item != null) {
                holder.bind(item, payloads)
            }
        }
    }

    override fun onAttachedToRecyclerView(recyclerView: RecyclerView) {
        super.onAttachedToRecyclerView(recyclerView)
        this@MemoryPagingAdapter.recyclerView = recyclerView
    }

    override fun onDetachedFromRecyclerView(recyclerView: RecyclerView) {
        super.onDetachedFromRecyclerView(recyclerView)
        this@MemoryPagingAdapter.recyclerView = null
    }

    fun refreshVisibleItems() {
        val layoutManager = recyclerView?.layoutManager as? LinearLayoutManager
        val firstVisible = layoutManager?.findFirstVisibleItemPosition() ?: return
        val lastVisible = layoutManager.findLastVisibleItemPosition()

        for (position in firstVisible..lastVisible) {
//            notifyItemChanged(position, listOf("time"))
        }
    }

    companion object {
        object MemoryComparator : DiffUtil.ItemCallback<MemoryItem>() {
            override fun areItemsTheSame(oldItem: MemoryItem, newItem: MemoryItem): Boolean {
                return oldItem.value.id == newItem.value.id
            }

            override fun areContentsTheSame(oldItem: MemoryItem, newItem: MemoryItem): Boolean {
                return oldItem.value.photoUrl == newItem.value.photoUrl
            }

            override fun getChangePayload(oldItem: MemoryItem, newItem: MemoryItem): Any? {
                val payload = mutableListOf<String>()

                if (oldItem.value.photoUrl != newItem.value.photoUrl) {
                    payload.add("photoUrl")
                }

                return if (payload.isEmpty()) null else payload
            }
        }
    }
}