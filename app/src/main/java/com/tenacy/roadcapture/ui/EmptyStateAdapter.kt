package com.tenacy.roadcapture.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.databinding.ViewDataBinding
import androidx.recyclerview.widget.RecyclerView
import com.tenacy.roadcapture.databinding.*
import com.tenacy.roadcapture.util.toPx

sealed class EmptyItem {
    data object Scrap: EmptyItem()
    data object Search: EmptyItem()
    data class MyAlbum(val paddingTop: Int? = null): EmptyItem()
    data object MyMemory: EmptyItem()
    data object Album: EmptyItem()
    data object Memory: EmptyItem()
}

sealed class EmptyViewHolder(binding: ViewDataBinding): RecyclerView.ViewHolder(binding.root) {
    class Scrap(
        binding: ItemScrapEmptyBinding,
    ): EmptyViewHolder(binding)

    class Search(
        binding: ItemSearchEmptyBinding,
    ): EmptyViewHolder(binding)

    class MyAlbum(
        private val binding: ItemMyAlbumEmptyBinding,
    ): EmptyViewHolder(binding) {
        fun bind(item: EmptyItem.MyAlbum) {
            item.paddingTop?.let {
                binding.root.apply {
                    setPadding(
                        paddingLeft,
                        it,
                        paddingRight,
                        paddingBottom,
                    )
                }
            }
        }
    }

    class MyMemory(
        binding: ItemMyMemoryEmptyBinding,
    ): EmptyViewHolder(binding)

    class Album(
        binding: ItemAlbumEmptyBinding,
    ): EmptyViewHolder(binding)

    class Memory(
        binding: ItemMemoryEmptyBinding,
    ): EmptyViewHolder(binding)
}

class EmptyStateAdapter(
    private val item: EmptyItem,
): RecyclerView.Adapter<EmptyViewHolder>() {
    var isVisible = false
        set(value) {
            if (field != value) {
                field = value
                notifyDataSetChanged()
            }
        }

    init {
        setHasStableIds(true)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): EmptyViewHolder {
        return when(viewType) {
            VIEW_TYPE_SCRAP -> EmptyViewHolder.Scrap(ItemScrapEmptyBinding.inflate(LayoutInflater.from(parent.context), parent, false))
            VIEW_TYPE_SEARCH -> EmptyViewHolder.Search(ItemSearchEmptyBinding.inflate(LayoutInflater.from(parent.context), parent, false))
            VIEW_TYPE_MY_ALBUM -> EmptyViewHolder.MyAlbum(ItemMyAlbumEmptyBinding.inflate(LayoutInflater.from(parent.context), parent, false))
            VIEW_TYPE_MY_MEMORY -> EmptyViewHolder.MyMemory(ItemMyMemoryEmptyBinding.inflate(LayoutInflater.from(parent.context), parent, false))
            VIEW_TYPE_ALBUM -> EmptyViewHolder.Album(ItemAlbumEmptyBinding.inflate(LayoutInflater.from(parent.context), parent, false))
            VIEW_TYPE_MEMORY -> EmptyViewHolder.Memory(ItemMemoryEmptyBinding.inflate(LayoutInflater.from(parent.context), parent, false))
            else -> throw IllegalStateException("뷰타입이 존재하지 않습니다.")
        }
    }

    override fun onBindViewHolder(holder: EmptyViewHolder, position: Int) {
        if(holder is EmptyViewHolder.MyAlbum && item is EmptyItem.MyAlbum) {
            holder.bind(item)
        }
    }

    override fun getItemCount(): Int = if (isVisible) 1 else 0

    override fun getItemViewType(position: Int): Int {
        return when(item) {
            is EmptyItem.Scrap -> VIEW_TYPE_SCRAP
            is EmptyItem.Search -> VIEW_TYPE_SEARCH
            is EmptyItem.MyAlbum -> VIEW_TYPE_MY_ALBUM
            is EmptyItem.MyMemory -> VIEW_TYPE_MY_MEMORY
            is EmptyItem.Album -> VIEW_TYPE_ALBUM
            is EmptyItem.Memory -> VIEW_TYPE_MEMORY
        }
    }

    override fun getItemId(position: Int): Long {
        return when(item) {
            is EmptyItem.Scrap -> VIEW_TYPE_SCRAP
            is EmptyItem.Search -> VIEW_TYPE_SEARCH
            is EmptyItem.MyAlbum -> VIEW_TYPE_MY_ALBUM
            is EmptyItem.MyMemory -> VIEW_TYPE_MY_MEMORY
            is EmptyItem.Album -> VIEW_TYPE_ALBUM
            is EmptyItem.Memory -> VIEW_TYPE_MEMORY
        }.toLong()
    }

    companion object {
        const val VIEW_TYPE_SCRAP = 0
        const val VIEW_TYPE_SEARCH = 1
        const val VIEW_TYPE_MY_ALBUM = 2
        const val VIEW_TYPE_MY_MEMORY = 3
        const val VIEW_TYPE_ALBUM = 4
        const val VIEW_TYPE_MEMORY = 5
    }
}