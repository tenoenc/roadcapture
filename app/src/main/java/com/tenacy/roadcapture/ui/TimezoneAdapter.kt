package com.tenacy.roadcapture.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.tenacy.roadcapture.databinding.ItemTimezoneBinding
import com.tenacy.roadcapture.ui.dto.SearchableTimezone

data class TimezoneItem(
    val id: Long,
    val value: SearchableTimezone,
    val onItemClick: (SearchableTimezone) -> Unit,
)

class TimezoneAdapter : ListAdapter<TimezoneItem, TimezoneViewHolder>(TimezoneComparator) {

    init {
        setHasStableIds(true)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TimezoneViewHolder {
        val binding = ItemTimezoneBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return TimezoneViewHolder(binding)
    }

    override fun onBindViewHolder(holder: TimezoneViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    override fun onBindViewHolder(holder: TimezoneViewHolder, position: Int, payloads: MutableList<Any>) {
        if (payloads.isEmpty()) {
            super.onBindViewHolder(holder, position, payloads)
        } else {
            val item = getItem(position)
            if (item != null) {
                holder.bind(item, payloads)
            }
        }
    }

    override fun getItemId(position: Int): Long {
        return getItem(position).id
    }

    companion object {
        object TimezoneComparator: DiffUtil.ItemCallback<TimezoneItem>() {
            override fun areItemsTheSame(oldItem: TimezoneItem, newItem: TimezoneItem): Boolean {
                return oldItem.id == newItem.id
            }

            override fun areContentsTheSame(oldItem: TimezoneItem, newItem: TimezoneItem): Boolean {
                return oldItem.value.isSelected == newItem.value.isSelected
            }

            override fun getChangePayload(oldItem: TimezoneItem, newItem: TimezoneItem): Any? {
                val payload = mutableListOf<String>()

                if (oldItem.value.isSelected != newItem.value.isSelected) {
                    payload.add("isSelected")
                }

                return if (payload.isEmpty()) null else payload
            }
        }
    }
}

class TimezoneViewHolder(
    private val binding: ItemTimezoneBinding
) : RecyclerView.ViewHolder(binding.root) {

    fun bind(item: TimezoneItem) {
        binding.selected = item.value.isSelected
        binding.value = item.value.localizedName
        binding.text = item.value.utcText
        binding.flag = item.value.flag
        binding.root.setOnClickListener { item.onItemClick(item.value) }
    }

    fun bind(item: TimezoneItem, payloads: List<Any>) {
        if (payloads.isEmpty()) {
            bind(item)
            return
        }

        payloads.forEach { payload ->
            if (payload is List<*>) {
                payload.forEach { change ->
                    when (change) {
                        "isSelected" -> {
                            binding.selected = item.value.isSelected
                        }
                    }
                }
            }
        }
    }
}